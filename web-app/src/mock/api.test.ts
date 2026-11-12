import { setupServer } from 'msw/node'
import { handlers } from './api'

const server = setupServer(...handlers)

beforeAll(() => {
	vi.stubGlobal('location', new URL('http://localhost/'))
	server.listen({ onUnhandledFrame: 'error' })
})
afterEach(() => server.resetHandlers())
afterAll(() => {
	server.close()
	vi.unstubAllGlobals()
})

test('mocks delete-records requests', async () => {
	const response = await fetch('http://localhost/api/kafka/delete-records', {
		method: 'POST',
		headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify({ topicName: 'test-topic', topicPartition: 0, beforeOffset: 10 })
	})

	expect(response.status).toBe(200)
	expect(await response.json()).toBeNull()
})

test('mocks consumer-group requests', async () => {
	const response = await fetch('http://localhost/api/kafka/get-consumer-groups', {
		method: 'POST',
		headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify({ topicName: 'test-topic-a' })
	})

	expect(response.status).toBe(200)
	expect(await response.json()).toEqual(['test-consumer-group-a', 'test-consumer-group-b'])
})
