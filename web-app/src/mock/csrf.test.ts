import { afterEach, expect, test, vi } from 'vitest'
import { initializeMockCsrfCookie } from './csrf'

afterEach(() => {
	vi.unstubAllGlobals()
})

test('initializes the CSRF cookie for mock requests', () => {
	const documentMock = { cookie: '' }
	vi.stubGlobal('document', documentMock)

	initializeMockCsrfCookie()

	expect(documentMock.cookie).toBe('XSRF-TOKEN=mock-csrf-token; Path=/; SameSite=Lax')
})
