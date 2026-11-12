import { TopicPartitionOffset } from '../../api'

export function findLaggingPartitions(
	consumerOffsets: TopicPartitionOffset[],
	logEndOffsets: TopicPartitionOffset[]
): Set<number> {
	const logEndOffsetByPartition = new Map<number, number>()
	for (const { topicPartition, offset } of logEndOffsets) {
		logEndOffsetByPartition.set(topicPartition, offset)
	}

	const laggingPartitions = new Set<number>()
	for (const { topicPartition, offset } of consumerOffsets) {
		const logEndOffset = logEndOffsetByPartition.get(topicPartition)
		// En committed offset før log-end betyr at consumeren ligger bak på partisjonen.
		if (logEndOffset !== undefined && offset < logEndOffset) {
			laggingPartitions.add(topicPartition)
		}
	}

	return laggingPartitions
}
