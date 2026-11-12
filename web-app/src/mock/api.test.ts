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

test('mocks consumer offsets with lag on partitions 0 and 2', async () => {
	const response = await fetch('http://localhost/api/kafka/get-consumer-offsets', {
		method: 'POST',
		headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify({ topicName: 'test-topic-a', groupId: 'test-consumer-group-a' })
	})

	expect(response.status).toBe(200)
	expect(await response.json()).toEqual([
		{ topicName: 'test-topic-a', topicPartition: 0, offset: 1799 },
		{ topicName: 'test-topic-a', topicPartition: 1, offset: 1781 },
		{ topicName: 'test-topic-a', topicPartition: 2, offset: 1725 },
		{ topicName: 'test-topic-a', topicPartition: 3, offset: 1865 }
	])
})

test('mocks last-record-offset requests for all partitions', async () => {
	const response = await fetch('http://localhost/api/kafka/get-last-record-offsets', {
		method: 'POST',
		headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify({ topicName: 'test-topic-a' })
	})

	expect(response.status).toBe(200)
	expect(await response.json()).toEqual([
		{ topicName: 'test-topic-a', topicPartition: 0, offset: 1801 },
		{ topicName: 'test-topic-a', topicPartition: 1, offset: 1781 },
		{ topicName: 'test-topic-a', topicPartition: 2, offset: 1730 },
		{ topicName: 'test-topic-a', topicPartition: 3, offset: 1865 }
	])
})
