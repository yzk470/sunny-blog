export const COVER_PRESETS = [
  { label: '清晨', value: '/covers/morning.svg' },
  { label: '春日', value: '/covers/spring.svg' },
  { label: '代码', value: '/covers/code.svg' },
  { label: '海边', value: '/covers/sea.svg' },
  { label: '咖啡', value: '/covers/cafe.svg' },
  { label: '阅读', value: '/covers/reading.svg' },
  { label: '晚霞', value: '/covers/sunset.svg' },
]

export const COLOR_PRESETS = [
  '#F7B7C8',
  '#FFD98E',
  '#8CC8E8',
  '#A8D8C8',
  '#C9C3F0',
  '#F3C6A5',
  '#B7D9B1',
  '#D9B8A6',
]

export const CATEGORY_EMOJI: Record<string, string> = {
  life: '🌿',
  tech: '💻',
  travel: '🌊',
  reading: '📖',
}

export function categoryEmoji(slug?: string | null): string {
  return (slug && CATEGORY_EMOJI[slug]) || '☀️'
}

export function coverOf(url?: string | null): string {
  return url && url.trim() ? url : '/covers/spring.svg'
}
