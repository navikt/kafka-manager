import { TopicPartitionOffset } from '../../api'
import { findLaggingPartitions } from './consumer-offsets'

test('finds partitions whose committed offsets are behind the log end', () => {
	const consumerOffsets: TopicPartitionOffset[] = [
		{ topicName: 'test-topic', topicPartition: 0, offset: 10 },
		{ topicName: 'test-topic', topicPartition: 1, offset: 20 },
		{ topicName: 'test-topic', topicPartition: 2, offset: 31 },
		{ topicName: 'test-topic', topicPartition: 3, offset: 5 }
	]
	const logEndOffsets: TopicPartitionOffset[] = [
		{ topicName: 'test-topic', topicPartition: 0, offset: 11 },
		{ topicName: 'test-topic', topicPartition: 1, offset: 20 },
		{ topicName: 'test-topic', topicPartition: 2, offset: 30 }
	]

	expect(findLaggingPartitions(consumerOffsets, logEndOffsets)).toEqual(new Set([0]))
})
