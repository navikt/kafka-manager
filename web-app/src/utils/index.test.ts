import { parseNonNegativeSafeInteger } from './index'

describe('parseNonNegativeSafeInteger', () => {
	it('parses a non-negative integer', () => {
		expect(parseNonNegativeSafeInteger('42')).toBe(42)
	})

	it.each(['', '-1', '1.5', '12abc', '9007199254740992'])('rejects invalid input: %s', value => {
		expect(parseNonNegativeSafeInteger(value)).toBeNull()
	})
})
