export function money(value) {
  const amount = Number(value);
  if (!Number.isFinite(amount)) return '—';
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 2,
  }).format(amount);
}

export function shortName(holder) {
  if (!holder) return 'Account';
  return holder.split('(')[0].trim();
}

export function deskName(holder) {
  const name = shortName(holder);
  const words = name.split(/\s+/).filter(Boolean);
  if (!words.length) return 'Account';
  if (name.length <= 14) return name;
  return words.length >= 3 ? words[words.length - 2] : words[0];
}

export function compact(value) {
  const amount = Number(value);
  if (!Number.isFinite(amount)) return '—';
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    notation: 'compact',
    maximumFractionDigits: 1,
  }).format(amount);
}

export function percent(value) {
  const amount = Number(value);
  if (!Number.isFinite(amount)) return '—';
  return `${Math.round(amount * 100)}%`;
}

export function clockTime(value) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}
