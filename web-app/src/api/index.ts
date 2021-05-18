import axios, { AxiosPromise } from 'axios';

export const axiosInstance = axios.create({
	withCredentials: true,
	headers: { 'Nav-Consumer-Id': 'kafka-manager' }
});

export interface User {
	ident: string;
	harTilgang: boolean;
}

export function me(): AxiosPromise<User> {
	return axiosInstance.get('/api/auth/me');
}

export interface ReadFromTopicRequest {
	username: string;
	password: string;
	topicName: string;
	topicPartition: number;
	maxRecords: number;
	fromOffset: number;
}

export interface GetConsumerOffsetsRequest {
	username: string;
	password: string;
	groupId: string;
	topicName: string;
}

export interface GetLastRecordOffsetRequest {
	username: string;
	password: string;
	topicName: string;
	topicPartition: number;
}

export interface SetConsumerOffsetRequest {
	username: string;
	password: string;
	topicName: string;
	topicPartition: number;
	offset: number;
	groupId: string;
}

export interface KafkaRecord {
	key: string | null;
	value: string | null;
	timestamp: number;
	offset: number;
	headers: { name: string; value: string }[];
}

export interface TopicPartitionOffset {
	topicName: string;
	topicPartition: number;
	offset: number;
}

export interface LastRecordOffsetResponse {
	latestRecordOffset: number;
}

export function readFromTopic(request: ReadFromTopicRequest): AxiosPromise<KafkaRecord[]> {
	return axiosInstance.post(`/api/kafka/read-topic`, {
		credentials: {
			username: request.username,
			password: request.password
		},
		topicName: request.topicName,
		topicPartition: request.topicPartition,
		maxRecords: request.maxRecords,
		fromOffset: request.fromOffset
	});
}

export function getConsumerOffsets(request: GetConsumerOffsetsRequest): AxiosPromise<TopicPartitionOffset[]> {
	return axiosInstance.post('/api/kafka/get-consumer-offsets', {
		credentials: {
			username: request.username,
			password: request.password
		},
		topicName: request.topicName,
		groupId: request.groupId
	});
}

export function getLastRecordOffset(request: GetLastRecordOffsetRequest): AxiosPromise<LastRecordOffsetResponse> {
	return axiosInstance.post('/api/kafka/get-last-record-offset', {
		credentials: {
			username: request.username,
			password: request.password
		},
		topicName: request.topicName,
		topicPartition: request.topicPartition
	});
}

export function setConsumerOffset(request: SetConsumerOffsetRequest): AxiosPromise {
	return axiosInstance.post('/api/kafka/set-consumer-offset', {
		credentials: {
			username: request.username,
			password: request.password
		},
		topicName: request.topicName,
		topicPartition: request.topicPartition,
		offset: request.offset,
		groupId: request.groupId
	});
}
