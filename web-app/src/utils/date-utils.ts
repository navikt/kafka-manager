import dayjs from 'dayjs';

export function formatDateTime(dateStr: string) {
	return dayjs(dateStr).format('DD. MMM YYYY kl. HH:mm');
}

export function toTimestamp(epochMillis: number) {
	return dayjs(epochMillis).format('DD. MMM YYYY kl. HH:mm:ss');
}

// 5400 => 5.4s
export function toTimerStr(millis: number) {
	const isNegative = millis < 0;
	millis = Math.abs(millis);

	const seconds = Math.floor(millis / 1000);
	const leftoverMillis = Math.floor((millis % 1000) / 100);

	return `${isNegative ? '-' : ''}${seconds}.${leftoverMillis}s`;
}
