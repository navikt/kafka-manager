import { toTimerStr } from './date-utils';

test('toTimerStr creates correct str with positive millis', () => {
	expect(toTimerStr(5300)).toBe('5.3s');
});

test('toTimerStr creates correct str with negative millis', () => {
	expect(toTimerStr(-5300)).toBe('-5.3s');
});

test('toTimerStr creates correct str with 0 millis', () => {
	expect(toTimerStr(0)).toBe('0.0s');
});
