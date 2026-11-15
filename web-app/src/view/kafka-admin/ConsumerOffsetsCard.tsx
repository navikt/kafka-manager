import {useEffect, useRef, useState} from 'react'
import {useQuery} from '@tanstack/react-query'
import {ExclamationmarkTriangleIcon} from '@navikt/aksel-icons'
import {errorToast, warningToast} from '../../utils/toast-utils'
import {Card} from '../../component/card/card'
import {BodyShort, Button, Table, VStack} from '@navikt/ds-react'
import {getConsumerOffsets, GetConsumerOffsetsRequest, getLastRecordOffsets} from '../../api'
import {SelectedTopic} from './SelectedTopic'
import {findLaggingPartitions} from './consumer-offsets'
import {ConsumerGroupCardProps, ConsumerGroupField} from './ConsumerGroupField'

export const ConsumerOffsetsCard = (props: ConsumerGroupCardProps) => {
    const [groupIdField, setGroupIdField] = useState('')
    const [submittedRequest, setSubmittedRequest] = useState<GetConsumerOffsetsRequest | null>(null)
    const consumerOffsetsQuery = useQuery({
        queryKey: ['consumer-offsets', submittedRequest?.topicName, submittedRequest?.groupId],
        queryFn: async () => {
            if (submittedRequest == null) {
                throw new Error('A topic and consumer group must be selected to load consumer offsets')
            }

            const [consumerOffsets, lastRecordOffsets] = await Promise.all([
                getConsumerOffsets(submittedRequest),
                getLastRecordOffsets({topicName: submittedRequest.topicName})
            ])
            return {consumerOffsets, lastRecordOffsets}
        },
        enabled: submittedRequest != null,
        retry: false,
        refetchOnWindowFocus: false
    })

    useEffect(() => {
        setGroupIdField('')
        setSubmittedRequest(null)
    }, [props.selectedTopic])

    const handleGroupIdChange = (groupId: string) => {
        setGroupIdField(groupId)
        setSubmittedRequest(null)
    }

    const handleHentConsumerOffsets = () => {
        if (props.selectedTopic == null) {
            errorToast('Please select a topic from the sidebar')
            return
        }

        const request: GetConsumerOffsetsRequest = {groupId: groupIdField, topicName: props.selectedTopic}
        const sameRequest =
            submittedRequest?.topicName === request.topicName && submittedRequest.groupId === request.groupId
        setSubmittedRequest(request)
        if (sameRequest) {
            void consumerOffsetsQuery.refetch()
        }
    }

    useEffect(() => {
        if (consumerOffsetsQuery.error && !consumerOffsetsQuery.isFetching) {
            errorToast('Klarte ikke å hente consumer offsets')
        }
    }, [consumerOffsetsQuery.error, consumerOffsetsQuery.isFetching])

    const warnedEmptyResultAt = useRef(0)
    useEffect(() => {
        if (
            consumerOffsetsQuery.isSuccess &&
            !consumerOffsetsQuery.isFetching &&
            consumerOffsetsQuery.data?.consumerOffsets.length === 0 &&
            consumerOffsetsQuery.dataUpdatedAt > 0 &&
            warnedEmptyResultAt.current !== consumerOffsetsQuery.dataUpdatedAt
        ) {
            warnedEmptyResultAt.current = consumerOffsetsQuery.dataUpdatedAt
            warningToast('Fant ingen offsets tilhørende konsumeren for topicen')
        }
    }, [
        consumerOffsetsQuery.data?.consumerOffsets.length,
        consumerOffsetsQuery.dataUpdatedAt,
        consumerOffsetsQuery.isFetching,
        consumerOffsetsQuery.isSuccess
    ])

    const isCurrentRequest =
        submittedRequest?.topicName === props.selectedTopic && submittedRequest.groupId === groupIdField
    const topicPartitionOffsets =
        isCurrentRequest && !consumerOffsetsQuery.isFetching && !consumerOffsetsQuery.isError
            ? consumerOffsetsQuery.data?.consumerOffsets ?? []
            : []
    const lastRecordOffsets =
        isCurrentRequest && !consumerOffsetsQuery.isFetching && !consumerOffsetsQuery.isError
            ? consumerOffsetsQuery.data?.lastRecordOffsets ?? []
            : []
    const laggingPartitions = findLaggingPartitions(topicPartitionOffsets, lastRecordOffsets)

    return (
        <Card title="Get consumer offsets" className="consumer-offset-card" innholdClassName="card__content">
            <BodyShort spacing>
                Henter siste commitet offset for alle partisjoner tilhørende en consumer gruppe for en gitt topic
            </BodyShort>
            <SelectedTopic selectedTopic={props.selectedTopic}/>
            <VStack gap="space-12">
                <ConsumerGroupField
                    groupId={groupIdField}
                    onGroupIdChange={handleGroupIdChange}
                    consumerGroups={props.consumerGroups}
                    consumerGroupsLoading={props.consumerGroupsLoading}
                    supportsConsumerGroupLookup={props.supportsConsumerGroupLookup}
                />
                <Button
                    onClick={handleHentConsumerOffsets}
                    variant="tertiary"
                    disabled={!props.selectedTopic || groupIdField.trim().length === 0}
                >
                    Fetch
                </Button>
            </VStack>
            {topicPartitionOffsets.length > 0 && (
                <Table size="small" className="kafka-admin__offset-table">
                    <Table.Header>
                        <Table.Row>
                            <Table.HeaderCell scope="col">Partition</Table.HeaderCell>
                            <Table.HeaderCell scope="col">Offset</Table.HeaderCell>
                        </Table.Row>
                    </Table.Header>
                    <Table.Body>
                        {[...topicPartitionOffsets]
                            .sort((a, b) => a.topicPartition - b.topicPartition)
                            .map(offset => (
                                <Table.Row key={offset.topicPartition}>
                                    <Table.HeaderCell scope="row">{offset.topicPartition}</Table.HeaderCell>
                                    <Table.DataCell>
										<span className="kafka-admin__offset-value">
											{offset.offset}
                                            {laggingPartitions.has(offset.topicPartition) && (
                                                <ExclamationmarkTriangleIcon
                                                    className="kafka-admin__lag-indicator"
                                                    title="Consumer offset is behind the log end"
                                                />
                                            )}
										</span>
                                    </Table.DataCell>
                                </Table.Row>
                            ))}
                    </Table.Body>
                </Table>
            )}
        </Card>
    )
}
