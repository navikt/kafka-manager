async function fetchApi(url: string, options: RequestInit = {}): Promise<Response> {
	const headers = new Headers(options.headers)
	headers.set('Nav-Consumer-Id', 'kafka-manager')

	const response = await fetch(url, {
		...options,
		// Behold cookie-autentiseringa som Axios withCredentials ga.
		credentials: 'include',
		headers
	})

	// Fetch godtar HTTP-feil, så vi må avvise promise-et selv.
	if (!response.ok) {
		throw new Error(`Request failed with status ${response.status}`)
	}

	return response
}

async function requestJson<T>(url: string, options?: RequestInit): Promise<T> {
	const response = await fetchApi(url, options)
	return response.json()
}

function postJson<T>(url: string, body: object): Promise<T> {
	return requestJson<T>(url, {
		method: 'POST',
		headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify(body)
	})
}

async function postVoid(url: string, body: object): Promise<void> {
	await fetchApi(url, {
		method: 'POST',
		headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify(body)
	})
}

export interface ReadFromTopicRequest {
	topicName: string
	topicAllPartitions: boolean
	topicPartition: number
	maxRecords: number
	fromOffset: number
	filterText?: string
}

export interface GetConsumerOffsetsRequest {
	groupId: string
	topicName: string
}

export interface GetConsumerGroupsRequest {
	topicName: string
}

export interface GetLastRecordOffsetRequest {
	topicName: string
	topicPartition: number
}

export interface GetLastRecordOffsetsRequest {
	topicName: string
}

export interface SetConsumerOffsetRequest {
	topicName: string
	topicPartition: number
	offset: number
	groupId: string
}

export interface KafkaRecord {
	partition: number
	key: string | null
	value: string | null
	timestamp: number
	offset: number
	headers: { name: string; value: string }[]
}

export interface TopicPartitionOffset {
	topicName: string
	topicPartition: number
	offset: number
}

export interface AvailableTopic {
	name: string
	supportsRecordDeletion: boolean
}

export interface LastRecordOffsetResponse {
	latestRecordOffset: number
}

export function getAvailableTopics(): Promise<AvailableTopic[]> {
	return requestJson('/api/kafka/available-topics')
}

export function readFromTopic(request: ReadFromTopicRequest): Promise<KafkaRecord[]> {
	return postJson('/api/kafka/read-topic', {
		topicName: request.topicName,
		topicPartition: request.topicPartition,
		topicAllPartitions: request.topicAllPartitions,
		maxRecords: request.maxRecords,
		fromOffset: request.fromOffset,
		filter: {
			text: request.filterText
		}
	})
}

export function getConsumerOffsets(request: GetConsumerOffsetsRequest): Promise<TopicPartitionOffset[]> {
	return postJson('/api/kafka/get-consumer-offsets', {
		topicName: request.topicName,
		groupId: request.groupId
	})
}

export function getConsumerGroups(request: GetConsumerGroupsRequest): Promise<string[]> {
	return postJson('/api/kafka/get-consumer-groups', {
		topicName: request.topicName
	})
}

export function getLastRecordOffset(request: GetLastRecordOffsetRequest): Promise<LastRecordOffsetResponse> {
	return postJson('/api/kafka/get-last-record-offset', {
		topicName: request.topicName,
		topicPartition: request.topicPartition
	})
}

export function getLastRecordOffsets(request: GetLastRecordOffsetsRequest): Promise<TopicPartitionOffset[]> {
	return postJson('/api/kafka/get-last-record-offsets', {
		topicName: request.topicName
	})
}

export function setConsumerOffset(request: SetConsumerOffsetRequest): Promise<void> {
	return postVoid('/api/kafka/set-consumer-offset', {
		topicName: request.topicName,
		topicPartition: request.topicPartition,
		offset: request.offset,
		groupId: request.groupId
	})
}

export interface DeleteRecordsRequest {
	topicName: string
	topicPartition: number
	beforeOffset: number
}

export function deleteConsumerRecords(request: DeleteRecordsRequest): Promise<void> {
	return postVoid('/api/kafka/delete-records', {
		topicName: request.topicName,
		topicPartition: request.topicPartition,
		beforeOffset: request.beforeOffset
	})
}
