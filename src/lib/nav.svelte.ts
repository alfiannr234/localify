import type { ViewRoute } from './types';

class NavStore {
  stack = $state<ViewRoute[]>([{ kind: 'home' }]);
  index = $state(0);
  searchQuery = $state('');

  current = $derived(this.stack[this.index] ?? { kind: 'home' });
  canBack = $derived(this.index > 0);
  canForward = $derived(this.index < this.stack.length - 1);

  go(route: ViewRoute): void {
    const cur = this.current;
    if (JSON.stringify(cur) === JSON.stringify(route)) return;
    const head = this.stack.slice(0, this.index + 1);
    this.stack = [...head, route];
    this.index = this.stack.length - 1;
  }

  back(): void {
    if (this.canBack) this.index -= 1;
  }

  forward(): void {
    if (this.canForward) this.index += 1;
  }
}

export const nav = new NavStore();
