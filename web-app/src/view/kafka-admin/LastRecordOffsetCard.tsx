import {useEffect, useState} from 'react'
import {useQuery} from '@tanstack/react-query'
import {errorToast} from '../../utils/toast-utils'
import {Card} from '../../component/card/card'
import {BodyShort, Button, Table, VStack} from '@navikt/ds-react'
import {getLastRecordOffsets} from '../../api'
import {SelectedTopic} from './SelectedTopic'

export const LastRecordOffsetCard = (props: { selectedTopic: string | null }) => {
    const [requestedTopic, setRequestedTopic] = useState<string | null>(null)
    const lastRecordOffsetsQuery = useQuery({
        queryKey: ['last-record-offsets', props.selectedTopic],
        queryFn: () => {
            if (props.selectedTopic == null) {
                throw new Error('A topic must be selected to load last record offsets')
            }

            return getLastRecordOffsets({topicName: props.selectedTopic})
        },
        enabled: false,
        retry: false,
        refetchOnWindowFocus: false
    })

    useEffect(() => {
        setRequestedTopic(null)
    }, [props.selectedTopic])

    useEffect(() => {
        if (
            requestedTopic === props.selectedTopic &&
            lastRecordOffsetsQuery.error &&
            !lastRecordOffsetsQuery.isFetching
        ) {
            errorToast('Klarte ikke å hente siste record offset')
        }
    }, [
        lastRecordOffsetsQuery.error,
        lastRecordOffsetsQuery.isFetching,
        props.selectedTopic,
        requestedTopic
    ])

    const handleFetchLastRecordOffsets = () => {
        if (props.selectedTopic == null) {
            errorToast('Please select a topic from the sidebar')
            return
        }

        setRequestedTopic(props.selectedTopic)
        void lastRecordOffsetsQuery.refetch()
    }

    const lastRecordOffsets =
        requestedTopic === props.selectedTopic &&
        !lastRecordOffsetsQuery.isFetching &&
        !lastRecordOffsetsQuery.isError
            ? lastRecordOffsetsQuery.data ?? []
            : []

    return (
        <Card title="Last record offsets" className="last-record-offset-card" innholdClassName="card__content">
            <BodyShort spacing>Henter offset for alle partisjoner i valgt topic</BodyShort>
            <SelectedTopic selectedTopic={props.selectedTopic}/>
            <VStack gap="space-12">
                <Button onClick={handleFetchLastRecordOffsets} variant="tertiary" disabled={!props.selectedTopic}>
                    Fetch
                </Button>
                {lastRecordOffsets.length > 0 && (
                    <Table size="small" className="kafka-admin__offset-table">
                        <Table.Header>
                            <Table.Row>
                                <Table.HeaderCell scope="col">Partition</Table.HeaderCell>
                                <Table.HeaderCell scope="col">Offset</Table.HeaderCell>
                            </Table.Row>
                        </Table.Header>
                        <Table.Body>
                            {[...lastRecordOffsets]
                                .sort((a, b) => a.topicPartition - b.topicPartition)
                                .map(offset => (
                                    <Table.Row key={offset.topicPartition}>
                                        <Table.HeaderCell scope="row">{offset.topicPartition}</Table.HeaderCell>
                                        <Table.DataCell>{offset.offset}</Table.DataCell>
                                    </Table.Row>
                                ))}
                        </Table.Body>
                    </Table>
                )}
            </VStack>
        </Card>
    )
}
