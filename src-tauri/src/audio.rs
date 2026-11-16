use cpal::traits::{DeviceTrait, HostTrait};
use rodio::{Decoder, OutputStream, OutputStreamHandle, Sink, Source};
use serde::{Deserialize, Serialize};
use std::fs::File;
use std::io::BufReader;
use std::path::Path;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::{mpsc, Arc, Mutex};
use std::time::Duration;
use tauri::{AppHandle, Emitter};

pub const EQ_BANDS_HZ: [f32; 10] = [
    32.0, 64.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0,
];

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct EqConfig {
    pub enabled: bool,
    pub preamp_db: f32,
    pub gains_db: [f32; 10],
}

impl Default for EqConfig {
    fn default() -> Self {
        Self {
            enabled: false,
            preamp_db: 0.0,
            gains_db: [0.0; 10],
        }
    }
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AudioTick {
    pub path: Option<String>,
    pub position: f64,
    pub duration: f64,
    pub playing: bool,
    pub volume: f32,
    pub muted: bool,
    pub speed: f32,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AudioDeviceInfo {
    pub name: String,
    pub is_default: bool,
}

#[derive(Clone, Copy, Default)]
struct BiquadCoeffs {
    b0: f32,
    b1: f32,
    b2: f32,
    a1: f32,
    a2: f32,
}

impl BiquadCoeffs {
    fn bypass() -> Self {
        Self {
            b0: 1.0,
            b1: 0.0,
            b2: 0.0,
            a1: 0.0,
            a2: 0.0,
        }
    }

    fn peaking(sample_rate: f32, freq: f32, q: f32, gain_db: f32) -> Self {
        if gain_db.abs() < 0.05 || freq >= sample_rate * 0.48 {
            return Self::bypass();
        }
        let a = 10.0_f32.powf(gain_db / 40.0);
        let omega = 2.0 * std::f32::consts::PI * freq / sample_rate;
        let sin_w = omega.sin();
        let cos_w = omega.cos();
        let alpha = sin_w / (2.0 * q.max(0.2));

        let b0 = 1.0 + alpha * a;
        let b1 = -2.0 * cos_w;
        let b2 = 1.0 - alpha * a;
        let a0 = 1.0 + alpha / a;
        let a1 = -2.0 * cos_w;
        let a2 = 1.0 - alpha / a;

        Self {
            b0: b0 / a0,
            b1: b1 / a0,
            b2: b2 / a0,
            a1: a1 / a0,
            a2: a2 / a0,
        }
    }

    #[inline]
    fn process(&self, x: f32, state: &mut [f32; 2]) -> f32 {
        let y = self.b0 * x + state[0];
        state[0] = self.b1 * x - self.a1 * y + state[1];
        state[1] = self.b2 * x - self.a2 * y;
        y
    }
}

struct SharedEq {
    generation: AtomicU64,
    config: Mutex<EqConfig>,
}

impl SharedEq {
    fn new() -> Self {
        Self {
            generation: AtomicU64::new(1),
            config: Mutex::new(EqConfig::default()),
        }
    }

    fn update(&self, cfg: EqConfig) {
        if let Ok(mut guard) = self.config.lock() {
            *guard = cfg;
            self.generation.fetch_add(1, Ordering::Release);
        }
    }
}

struct ClockTracker {
    seek_anchor_us: AtomicU64,
    samples_since_anchor: AtomicU64,
    sample_rate: AtomicU64,
    channels: AtomicU64,
    duration_us: AtomicU64,
    playing: AtomicBool,
    user_stopped: AtomicBool,
    ended_emitted: AtomicBool,
    current_path: Mutex<Option<String>>,
    volume: Mutex<f32>,
    muted: AtomicBool,
    speed: Mutex<f32>,
}

impl ClockTracker {
    fn new() -> Self {
        Self {
            seek_anchor_us: AtomicU64::new(0),
            samples_since_anchor: AtomicU64::new(0),
            sample_rate: AtomicU64::new(44100),
            channels: AtomicU64::new(2),
            duration_us: AtomicU64::new(0),
            playing: AtomicBool::new(false),
            user_stopped: AtomicBool::new(true),
            ended_emitted: AtomicBool::new(false),
            current_path: Mutex::new(None),
            volume: Mutex::new(1.0),
            muted: AtomicBool::new(false),
            speed: Mutex::new(1.0),
        }
    }

    fn position_secs(&self) -> f64 {
        let sr = self.sample_rate.load(Ordering::Relaxed).max(1);
        let ch = self.channels.load(Ordering::Relaxed).max(1);
        let anchor_us = self.seek_anchor_us.load(Ordering::Relaxed);
        let samples = self.samples_since_anchor.load(Ordering::Relaxed);
        let played_us = (samples as u128 * 1_000_000) / ((sr as u128) * (ch as u128));
        (anchor_us as f64 + played_us as f64) / 1_000_000.0
    }

    fn duration_secs(&self) -> f64 {
        self.duration_us.load(Ordering::Relaxed) as f64 / 1_000_000.0
    }

    fn snapshot(&self) -> AudioTick {
        let path = self.current_path.lock().ok().and_then(|g| g.clone());
        let volume = self.volume.lock().map(|g| *g).unwrap_or(1.0);
        let speed = self.speed.lock().map(|g| *g).unwrap_or(1.0);
        AudioTick {
            path,
            position: self.position_secs().min(self.duration_secs().max(f64::MAX)),
            duration: self.duration_secs(),
            playing: self.playing.load(Ordering::Relaxed),
            volume,
            muted: self.muted.load(Ordering::Relaxed),
            speed,
        }
    }
}

struct DspSource<S> {
    inner: S,
    channels: u16,
    sample_rate: u32,
    ch_idx: usize,
    sample_count: u64,
    eq_gen: u64,
    eq_enabled: bool,
    preamp: f32,
    coeffs: [BiquadCoeffs; 10],
    states: [[f32; 2]; 20], // up to 2 channels x 10 bands
    shared_eq: Arc<SharedEq>,
    clock: Arc<ClockTracker>,
    source_eof: Arc<AtomicBool>,
}

impl<S: Source<Item = f32>> DspSource<S> {
    fn new(
        inner: S,
        shared_eq: Arc<SharedEq>,
        clock: Arc<ClockTracker>,
        source_eof: Arc<AtomicBool>,
    ) -> Self {
        let channels = inner.channels().max(1);
        let sample_rate = inner.sample_rate().max(8000);
        let mut s = Self {
            inner,
            channels,
            sample_rate,
            ch_idx: 0,
            sample_count: 0,
            eq_gen: 0,
            eq_enabled: false,
            preamp: 1.0,
            coeffs: [BiquadCoeffs::bypass(); 10],
            states: [[0.0; 2]; 20],
            shared_eq,
            clock,
            source_eof,
        };
        s.sync_eq();
        s
    }

    fn sync_eq(&mut self) {
        let gen = self.shared_eq.generation.load(Ordering::Acquire);
        if gen == self.eq_gen {
            return;
        }
        if let Ok(cfg) = self.shared_eq.config.lock() {
            self.eq_enabled = cfg.enabled;
            self.preamp = 10.0_f32.powf(cfg.preamp_db.clamp(-12.0, 12.0) / 20.0);
            for i in 0..10 {
                let gain = cfg.gains_db[i].clamp(-12.0, 12.0);
                self.coeffs[i] =
                    BiquadCoeffs::peaking(self.sample_rate as f32, EQ_BANDS_HZ[i], 1.2, gain);
            }
            self.eq_gen = gen;
        }
    }
}

impl<S: Source<Item = f32>> Iterator for DspSource<S> {
    type Item = f32;

    fn next(&mut self) -> Option<f32> {
        let sample = match self.inner.next() {
            Some(s) => s,
            None => {
                self.source_eof.store(true, Ordering::Release);
                return None;
            }
        };
        self.clock.samples_since_anchor.fetch_add(1, Ordering::Relaxed);
        self.sample_count = self.sample_count.wrapping_add(1);
        if self.sample_count & 511 == 0 {
            self.sync_eq();
        }

        let ch = (self.ch_idx % 2) as usize;
        self.ch_idx = (self.ch_idx + 1) % self.channels as usize;

        if !self.eq_enabled {
            return Some(sample);
        }

        let mut y = sample * self.preamp;
        for b in 0..10 {
            let st_idx = ch * 10 + b;
            y = self.coeffs[b].process(y, &mut self.states[st_idx]);
        }
        // Soft limiter prevents harsh digital clipping on high EQ boosts
        if y.abs() > 0.96 {
            y = y.signum() * (0.96 + 0.04 * ((y.abs() - 0.96) / 0.25).tanh());
        }
        Some(y)
    }
}

impl<S: Source<Item = f32>> Source for DspSource<S> {
    fn current_frame_len(&self) -> Option<usize> {
        self.inner.current_frame_len()
    }
    fn channels(&self) -> u16 {
        self.channels
    }
    fn sample_rate(&self) -> u32 {
        self.sample_rate
    }
    fn total_duration(&self) -> Option<Duration> {
        self.inner.total_duration()
    }
    fn try_seek(&mut self, pos: Duration) -> Result<(), rodio::source::SeekError> {
        self.inner.try_seek(pos)?;
        self.states = [[0.0; 2]; 20];
        self.ch_idx = 0;
        self.clock
            .seek_anchor_us
            .store(pos.as_micros() as u64, Ordering::Relaxed);
        self.clock.samples_since_anchor.store(0, Ordering::Relaxed);
        Ok(())
    }
}

enum AudioCmd {
    Play {
        path: String,
        start_secs: f64,
        reply: mpsc::SyncSender<Result<(), String>>,
    },
    Pause {
        paused: bool,
    },
    Seek {
        secs: f64,
    },
    Stop,
    SetVolume(f32),
    SetMute(bool),
    SetSpeed(f32),
    SetDevice {
        name: Option<String>,
        reply: mpsc::SyncSender<Result<(), String>>,
    },
}

pub struct AudioEngine {
    tx: mpsc::Sender<AudioCmd>,
    eq: Arc<SharedEq>,
    clock: Arc<ClockTracker>,
}

impl AudioEngine {
    pub fn start(app: AppHandle) -> Self {
        let (tx, rx) = mpsc::channel::<AudioCmd>();
        let eq = Arc::new(SharedEq::new());
        let clock = Arc::new(ClockTracker::new());

        let eq_worker = Arc::clone(&eq);
        let clock_worker = Arc::clone(&clock);
        let app_worker = app.clone();

        std::thread::spawn(move || {
            run_audio_thread(rx, eq_worker, clock_worker, app_worker);
        });

        Self { tx, eq, clock }
    }

    pub fn play(&self, path: String, start_secs: f64) -> Result<(), String> {
        let (reply, rx) = mpsc::sync_channel(1);
        self.tx
            .send(AudioCmd::Play {
                path,
                start_secs,
                reply,
            })
            .map_err(|e| e.to_string())?;
        rx.recv().map_err(|e| e.to_string())?
    }

    pub fn set_paused(&self, paused: bool) {
        let _ = self.tx.send(AudioCmd::Pause { paused });
    }

    pub fn seek(&self, secs: f64) {
        let _ = self.tx.send(AudioCmd::Seek { secs });
    }

    pub fn stop(&self) {
        let _ = self.tx.send(AudioCmd::Stop);
    }

    pub fn set_volume(&self, vol: f32) {
        let _ = self.tx.send(AudioCmd::SetVolume(vol.clamp(0.0, 1.0)));
    }

    pub fn set_mute(&self, muted: bool) {
        let _ = self.tx.send(AudioCmd::SetMute(muted));
    }

    pub fn set_speed(&self, speed: f32) {
        let _ = self.tx.send(AudioCmd::SetSpeed(speed.clamp(0.25, 2.0)));
    }

    pub fn set_eq(&self, cfg: EqConfig) {
        self.eq.update(cfg);
    }

    pub fn state(&self) -> AudioTick {
        self.clock.snapshot()
    }

    pub fn set_device(&self, name: Option<String>) -> Result<(), String> {
        let (reply, rx) = mpsc::sync_channel(1);
        self.tx
            .send(AudioCmd::SetDevice { name, reply })
            .map_err(|e| e.to_string())?;
        rx.recv().map_err(|e| e.to_string())?
    }
}

pub fn list_output_devices() -> Vec<AudioDeviceInfo> {
    let host = cpal::default_host();
    let default_name = host.default_output_device().and_then(|d| d.name().ok());
    let Ok(devices) = host.output_devices() else {
        return Vec::new();
    };
    devices
        .filter_map(|d| d.name().ok())
        .map(|name| AudioDeviceInfo {
            is_default: default_name.as_deref() == Some(name.as_str()),
            name,
        })
        .collect()
}

fn open_output(device_name: Option<&str>) -> Result<(OutputStream, OutputStreamHandle), String> {
    if let Some(target) = device_name {
        let host = cpal::default_host();
        if let Ok(mut devices) = host.output_devices() {
            if let Some(dev) = devices.find(|d| d.name().ok().as_deref() == Some(target)) {
                if let Ok(pair) = OutputStream::try_from_device(&dev) {
                    return Ok(pair);
                }
            }
        }
    }
    OutputStream::try_default().map_err(|e| format!("Failed to open audio device: {e}"))
}

fn run_audio_thread(
    rx: mpsc::Receiver<AudioCmd>,
    eq: Arc<SharedEq>,
    clock: Arc<ClockTracker>,
    app: AppHandle,
) {
    let mut output_pair = open_output(None).ok();
    let mut sink: Option<Sink> = None;
    let mut current_device: Option<String> = None;
    let mut source_eof = Arc::new(AtomicBool::new(false));
    let mut current_vol: f32 = 1.0;
    let mut is_muted = false;
    let mut current_speed: f32 = 1.0;

    let mut last_tick = std::time::Instant::now();

    loop {
        let is_playing_now = clock.playing.load(Ordering::Relaxed);
        let wait = if is_playing_now {
            Duration::from_millis(16)
        } else {
            Duration::from_millis(150)
        };
        let first_cmd = rx.recv_timeout(wait).ok();
        for cmd in first_cmd.into_iter().chain(rx.try_iter()) {
            match cmd {
                AudioCmd::Play {
                    path,
                    start_secs,
                    reply,
                } => {
                    if output_pair.is_none() {
                        output_pair = open_output(current_device.as_deref()).ok();
                    }
                    let res = (|| -> Result<(), String> {
                        let (_, handle) = output_pair
                            .as_ref()
                            .ok_or_else(|| "No audio output device available".to_string())?;
                        if let Some(old) = sink.take() {
                            old.stop();
                        }

                        let file = File::open(Path::new(&path))
                            .map_err(|e| format!("Cannot open audio file: {e}"))?;
                        let decoder = Decoder::new(BufReader::new(file))
                            .map_err(|e| format!("Unsupported or corrupt audio file: {e}"))?;

                        let sr = decoder.sample_rate().max(8000);
                        let ch = decoder.channels().max(1);
                        let dur_us = decoder
                            .total_duration()
                            .map(|d| d.as_micros() as u64)
                            .unwrap_or(0);

                        let eof_flag = Arc::new(AtomicBool::new(false));
                        source_eof = Arc::clone(&eof_flag);

                        clock.sample_rate.store(sr as u64, Ordering::Relaxed);
                        clock.channels.store(ch as u64, Ordering::Relaxed);
                        clock.duration_us.store(dur_us, Ordering::Relaxed);
                        clock.seek_anchor_us.store(0, Ordering::Relaxed);
                        clock.samples_since_anchor.store(0, Ordering::Relaxed);
                        clock.user_stopped.store(false, Ordering::Relaxed);
                        clock.ended_emitted.store(false, Ordering::Relaxed);
                        if let Ok(mut p) = clock.current_path.lock() {
                            *p = Some(path.clone());
                        }

                        let mut dsp = DspSource::new(
                            decoder.convert_samples::<f32>(),
                            Arc::clone(&eq),
                            Arc::clone(&clock),
                            eof_flag,
                        );

                        if start_secs > 0.05 {
                            let target = Duration::from_secs_f64(start_secs);
                            let _ = dsp.try_seek(target);
                        }

                        let new_sink = Sink::try_new(handle)
                            .map_err(|e| format!("Failed to create audio sink: {e}"))?;
                        new_sink.set_volume(if is_muted { 0.0 } else { current_vol });
                        new_sink.set_speed(current_speed);
                        new_sink.append(dsp);
                        new_sink.play();

                        clock.playing.store(true, Ordering::Relaxed);
                        sink = Some(new_sink);
                        Ok(())
                    })();
                    let _ = reply.send(res);
                }
                AudioCmd::Pause { paused } => {
                    if let Some(s) = &sink {
                        if paused {
                            s.pause();
                            clock.playing.store(false, Ordering::Relaxed);
                        } else {
                            s.play();
                            clock.playing.store(true, Ordering::Relaxed);
                        }
                        let _ = app.emit("audio-tick", clock.snapshot());
                    }
                }
                AudioCmd::Seek { secs } => {
                    if let Some(s) = &sink {
                        let clamped = secs.max(0.0);
                        let target = Duration::from_secs_f64(clamped);
                        if s.try_seek(target).is_ok() {
                            clock
                                .seek_anchor_us
                                .store(target.as_micros() as u64, Ordering::Relaxed);
                            clock.samples_since_anchor.store(0, Ordering::Relaxed);
                            clock.ended_emitted.store(false, Ordering::Relaxed);
                            let _ = app.emit("audio-tick", clock.snapshot());
                        }
                    }
                }
                AudioCmd::Stop => {
                    clock.user_stopped.store(true, Ordering::Relaxed);
                    clock.playing.store(false, Ordering::Relaxed);
                    clock.samples_since_anchor.store(0, Ordering::Relaxed);
                    clock.seek_anchor_us.store(0, Ordering::Relaxed);
                    if let Some(old) = sink.take() {
                        old.stop();
                    }
                    if let Ok(mut p) = clock.current_path.lock() {
                        *p = None;
                    }
                    let _ = app.emit("audio-tick", clock.snapshot());
                }
                AudioCmd::SetVolume(vol) => {
                    current_vol = vol;
                    if let Ok(mut v) = clock.volume.lock() {
                        *v = vol;
                    }
                    if let Some(s) = &sink {
                        s.set_volume(if is_muted { 0.0 } else { current_vol });
                    }
                }
                AudioCmd::SetMute(muted) => {
                    is_muted = muted;
                    clock.muted.store(muted, Ordering::Relaxed);
                    if let Some(s) = &sink {
                        s.set_volume(if is_muted { 0.0 } else { current_vol });
                    }
                }
                AudioCmd::SetSpeed(speed) => {
                    current_speed = speed;
                    if let Ok(mut sp) = clock.speed.lock() {
                        *sp = speed;
                    }
                    if let Some(s) = &sink {
                        s.set_speed(speed);
                    }
                }
                AudioCmd::SetDevice { name, reply } => {
                    let was_playing = clock.playing.load(Ordering::Relaxed);
                    let pos = clock.position_secs();
                    let path = clock.current_path.lock().ok().and_then(|g| g.clone());

                    if let Some(old) = sink.take() {
                        old.stop();
                    }
                    drop(output_pair.take());

                    current_device = name.clone();
                    let opened = open_output(name.as_deref());
                    match opened {
                        Ok(pair) => {
                            output_pair = Some(pair);
                            if let Some(track_path) = path {
                                if let Some((_, handle)) = &output_pair {
                                    if let Ok(file) = File::open(Path::new(&track_path)) {
                                        if let Ok(decoder) = Decoder::new(BufReader::new(file)) {
                                            let eof_flag = Arc::new(AtomicBool::new(false));
                                            source_eof = Arc::clone(&eof_flag);
                                            let mut dsp = DspSource::new(
                                                decoder.convert_samples::<f32>(),
                                                Arc::clone(&eq),
                                                Arc::clone(&clock),
                                                eof_flag,
                                            );
                                            if pos > 0.05 {
                                                let _ = dsp.try_seek(Duration::from_secs_f64(pos));
                                            }
                                            if let Ok(ns) = Sink::try_new(handle) {
                                                ns.set_volume(if is_muted { 0.0 } else { current_vol });
                                                ns.set_speed(current_speed);
                                                ns.append(dsp);
                                                if was_playing {
                                                    ns.play();
                                                } else {
                                                    ns.pause();
                                                }
                                                sink = Some(ns);
                                            }
                                        }
                                    }
                                }
                            }
                            let _ = reply.send(Ok(()));
                        }
                        Err(e) => {
                            let _ = reply.send(Err(e));
                        }
                    }
                }
            }
        }

        if let Some(s) = &sink {
            if s.empty()
                && source_eof.load(Ordering::Acquire)
                && !clock.user_stopped.load(Ordering::Relaxed)
                && !clock.ended_emitted.swap(true, Ordering::AcqRel)
            {
                clock.playing.store(false, Ordering::Relaxed);
                let _ = app.emit("audio-tick", clock.snapshot());
                let _ = app.emit("track-ended", ());
            }
        }

        if last_tick.elapsed() >= Duration::from_millis(200) {
            if clock.playing.load(Ordering::Relaxed) {
                let _ = app.emit("audio-tick", clock.snapshot());
            }
            last_tick = std::time::Instant::now();
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn peaking_biquad_unity_at_zero_db_and_finite_at_extremes() {
        let c0 = BiquadCoeffs::peaking(48000.0, 1000.0, 1.2, 0.0);
        assert_eq!(c0.b0, 1.0);
        assert_eq!(c0.b1, 0.0);

        let c_boost = BiquadCoeffs::peaking(48000.0, 64.0, 1.2, 9.0);
        let mut st = [0.0_f32; 2];
        for i in 0..500 {
            let x = (i as f32 * 0.05).sin() * 0.5;
            let y = c_boost.process(x, &mut st);
            assert!(y.is_finite());
        }
    }
}
