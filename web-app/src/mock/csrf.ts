export function initializeMockCsrfCookie(): void {
	document.cookie = 'XSRF-TOKEN=mock-csrf-token; Path=/; SameSite=Lax'
}
