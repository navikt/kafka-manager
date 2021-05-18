import dayjs from 'dayjs';

export function formatDateTime(dateStr: string) {
	return dayjs(dateStr).format('DD. MMM YYYY kl. HH:mm');
}

export function toTimestamp(epochMillis: number) {
	return dayjs(epochMillis).format('DD. MMM YYYY kl. HH:mm:ss');
}
