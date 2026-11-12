import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import {
	deleteConsumerRecords,
	getAvailableTopics,
	getConsumerGroups,
	getConsumerOffsets,
	getLastRecordOffset,
	getLastRecordOffsets,
	readFromTopic,
	setConsumerOffset
} from './index'

const fetchMock = vi.fn<typeof fetch>()

function jsonResponse(body: unknown, status = 200): Response {
	return new Response(JSON.stringify(body), {
		status,
		headers: { 'Content-Type': 'application/json' }
	})
}

function expectPost(url: string, body: object) {
	const [requestUrl, options] = fetchMock.mock.calls[fetchMock.mock.calls.length - 1]
	expect(requestUrl).toBe(url)
	expect(options?.method).toBe('POST')
	expect(options?.body).toBe(JSON.stringify(body))
}

beforeEach(() => {
	fetchMock.mockReset()
	vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
	vi.unstubAllGlobals()
})

test('includes cookies and the Nav consumer header', async () => {
	const topics = [{ name: 'test-topic', supportsRecordDeletion: false }]
	fetchMock.mockResolvedValueOnce(jsonResponse(topics))

	await expect(getAvailableTopics()).resolves.toEqual(topics)

	const [, options] = fetchMock.mock.calls[0]
	expect(options?.credentials).toBe('include')
	expect(new Headers(options?.headers).get('Nav-Consumer-Id')).toBe('kafka-manager')
})

test('posts JSON and returns the decoded response', async () => {
	const request = { topicName: 'test-topic', groupId: 'test-group' }
	const offsets = [{ topicName: 'test-topic', topicPartition: 0, offset: 10 }]
	fetchMock.mockResolvedValueOnce(jsonResponse(offsets))

	await expect(getConsumerOffsets(request)).resolves.toEqual(offsets)

	const [url, options] = fetchMock.mock.calls[0]
	expect(url).toBe('/api/kafka/get-consumer-offsets')
	expect(options?.method).toBe('POST')
	expect(options?.body).toBe(JSON.stringify(request))
	expect(new Headers(options?.headers).get('Content-Type')).toBe('application/json')
})

test('posts read-topic filters in the expected shape', async () => {
	const request = {
		topicName: 'test-topic',
		topicAllPartitions: true,
		topicPartition: 0,
		maxRecords: 10,
		fromOffset: 3,
		filterText: 'matching text'
	}
	fetchMock.mockResolvedValueOnce(jsonResponse([]))

	await expect(readFromTopic(request)).resolves.toEqual([])

	expectPost('/api/kafka/read-topic', {
		topicName: 'test-topic',
		topicPartition: 0,
		topicAllPartitions: true,
		maxRecords: 10,
		fromOffset: 3,
		filter: { text: 'matching text' }
	})
})

test('posts consumer-group lookups to the expected endpoint', async () => {
	fetchMock.mockResolvedValueOnce(jsonResponse(['test-group']))

	await expect(getConsumerGroups({ topicName: 'test-topic' })).resolves.toEqual(['test-group'])

	expectPost('/api/kafka/get-consumer-groups', { topicName: 'test-topic' })
})

test('posts a single-partition last-record-offset request', async () => {
	fetchMock.mockResolvedValueOnce(jsonResponse({ latestRecordOffset: 12 }))

	await expect(getLastRecordOffset({ topicName: 'test-topic', topicPartition: 2 })).resolves.toEqual({
		latestRecordOffset: 12
	})

	expectPost('/api/kafka/get-last-record-offset', { topicName: 'test-topic', topicPartition: 2 })
})

test('posts all-partition last-record-offset requests', async () => {
	fetchMock.mockResolvedValueOnce(jsonResponse([]))

	await expect(getLastRecordOffsets({ topicName: 'test-topic' })).resolves.toEqual([])

	expectPost('/api/kafka/get-last-record-offsets', { topicName: 'test-topic' })
})

test('rejects responses with an HTTP error status', async () => {
	fetchMock.mockResolvedValueOnce(new Response(null, { status: 500 }))

	await expect(getAvailableTopics()).rejects.toThrow('Request failed with status 500')
})

test('accepts empty successful responses for commands', async () => {
	fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

	await expect(
		setConsumerOffset({ topicName: 'test-topic', topicPartition: 0, offset: 10, groupId: 'test-group' })
	).resolves.toBeUndefined()

	expectPost('/api/kafka/set-consumer-offset', {
		topicName: 'test-topic',
		topicPartition: 0,
		offset: 10,
		groupId: 'test-group'
	})
})

test('accepts empty successful responses when deleting records', async () => {
	fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

	await expect(
		deleteConsumerRecords({ topicName: 'test-topic', topicPartition: 0, beforeOffset: 10 })
	).resolves.toBeUndefined()

	expectPost('/api/kafka/delete-records', {
		topicName: 'test-topic',
		topicPartition: 0,
		beforeOffset: 10
	})
})
