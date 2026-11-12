export const NO_OP = () => {}

export const isJson = (maybeJson: string): boolean => {
	try {
		JSON.parse(maybeJson)
		return true
	} catch {
		return false
	}
}

export function parseNonNegativeSafeInteger(value: string): number | null {
	const trimmedValue = value.trim()
	if (!/^\d+$/.test(trimmedValue)) {
		return null
	}

	const parsedValue = Number(trimmedValue)
	return Number.isSafeInteger(parsedValue) ? parsedValue : null
}
