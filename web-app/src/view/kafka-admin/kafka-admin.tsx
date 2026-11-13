import {lazy, Suspense, useEffect, useLayoutEffect, useRef, useState} from 'react'
import {ExclamationmarkTriangleIcon} from '@navikt/aksel-icons'
import {errorToast, successToast, warningToast} from '../../utils/toast-utils'
import {Card} from '../../component/card/card'
import {
    BodyShort,
    Button,
    Label,
    Loader,
    Modal,
    Table,
    TextField,
    ToggleGroup,
    UNSAFE_Combobox,
    VStack
} from '@navikt/ds-react'
import {
    AvailableTopic,
    getAvailableTopics,
    getConsumerGroups,
    getConsumerOffsets,
    GetConsumerOffsetsRequest,
    getLastRecordOffset,
    getLastRecordOffsets,
    GetLastRecordOffsetsRequest,
    KafkaRecord,
    readFromTopic,
    ReadFromTopicRequest,
    setConsumerOffset,
    SetConsumerOffsetRequest,
    TopicPartitionOffset
} from '../../api'
import './kafka-admin.css'
import {PageSpinner} from '../../component/page-spinner/page-spinner'
import {parseNonNegativeSafeInteger} from '../../utils'
import {toTimerStr} from '../../utils/date-utils'
import {DeleteRecordsCard} from './DeleteRecordsCard'
import {SelectedTopic} from './SelectedTopic'
import {findLaggingPartitions} from './consumer-offsets'

const KafkaRecordModalContent = lazy(() =>
    import('./kafka-record-modal-content').then(({KafkaRecordModalContent}) => ({
        default: KafkaRecordModalContent
    }))
)

export function KafkaAdmin() {
    const [availableTopics, setAvailableTopics] = useState<AvailableTopic[] | null>(null)
    const [selectedTopic, setSelectedTopic] = useState<string | null>(null)
    const [consumerGroups, setConsumerGroups] = useState<{ topicName: string; groups: string[] } | null>(null)
    const [loadingConsumerGroupsTopic, setLoadingConsumerGroupsTopic] = useState<string | null>(null)

    useEffect(() => {
        getAvailableTopics()
            .then(topics => setAvailableTopics(topics))
            .catch(() => {
                errorToast('Unable to load available topics')
                setAvailableTopics([])
            })
    }, [])

    useEffect(() => {
        const topic = availableTopics?.find(availableTopic => availableTopic.name === selectedTopic)
        if (!topic?.supportsRecordDeletion) {
            setConsumerGroups(null)
            setLoadingConsumerGroupsTopic(null)
            return
        }

        let isCurrentRequest = true
        setConsumerGroups(null)
        setLoadingConsumerGroupsTopic(topic.name)
        getConsumerGroups({topicName: topic.name})
            .then(groups => {
                if (isCurrentRequest) {
                    setConsumerGroups({topicName: topic.name, groups})
                }
            })
            .catch(() => {
                if (isCurrentRequest) {
                    setConsumerGroups({topicName: topic.name, groups: []})
                    errorToast('Unable to load consumer groups')
                }
            })
            .finally(() => {
                if (isCurrentRequest) {
                    setLoadingConsumerGroupsTopic(null)
                }
            })

        return () => {
            isCurrentRequest = false
        }
    }, [availableTopics, selectedTopic])

    if (availableTopics == null) {
        return <PageSpinner/>
    }

    const sortedTopics = [...availableTopics].sort((a, b) => a.name.localeCompare(b.name))
    const selectedTopicDetails = availableTopics.find(topic => topic.name === selectedTopic)
    const consumerGroupOptions = consumerGroups?.topicName === selectedTopic ? consumerGroups.groups : []
    const isConsumerGroupsLoading = selectedTopic != null && loadingConsumerGroupsTopic === selectedTopic
    const supportsConsumerGroupLookup = selectedTopicDetails?.supportsRecordDeletion ?? false

    return (
        <div className="view kafka-admin">
            <div className="kafka-admin__topic-selector">
                <UNSAFE_Combobox
                    label="Select Kafka Topic"
                    options={sortedTopics.map(topic => topic.name)}
                    onToggleSelected={option => setSelectedTopic(option)}
                    selectedOptions={selectedTopic ? [selectedTopic] : []}
                    isMultiSelect={false}
                    shouldAutocomplete={true}
                />
            </div>
            <div className="kafka-admin__content">
                <div className="kafka-admin__col">
                    <LastRecordOffsetCard selectedTopic={selectedTopic}/>
                    <ConsumerOffsetsCard
                        selectedTopic={selectedTopic}
                        consumerGroups={consumerGroupOptions}
                        consumerGroupsLoading={isConsumerGroupsLoading}
                        supportsConsumerGroupLookup={supportsConsumerGroupLookup}
                    />
                    <SetConsumerOffsetCard
                        selectedTopic={selectedTopic}
                        consumerGroups={consumerGroupOptions}
                        consumerGroupsLoading={isConsumerGroupsLoading}
                        supportsConsumerGroupLookup={supportsConsumerGroupLookup}
                    />
                </div>
                <div>
                    <ReadFromTopicCard selectedTopic={selectedTopic}/>
                    <DeleteRecordsCard
                        selectedTopic={selectedTopic}
                        supportsRecordDeletion={selectedTopicDetails?.supportsRecordDeletion ?? false}
                    />
                </div>
            </div>
        </div>
    )
}

interface ConsumerGroupFieldProps {
    groupId: string
    onGroupIdChange: (groupId: string) => void
    consumerGroups: string[]
    consumerGroupsLoading: boolean
    supportsConsumerGroupLookup: boolean
}

function ConsumerGroupField(props: ConsumerGroupFieldProps) {
    if (!props.supportsConsumerGroupLookup) {
        return (
            <TextField
                label="Consumer group id"
                value={props.groupId}
                onChange={event => props.onGroupIdChange(event.target.value)}
            />
        )
    }

    return (
        <UNSAFE_Combobox
            label="Consumer group id"
            options={props.consumerGroups}
            selectedOptions={props.groupId ? [props.groupId] : []}
            onToggleSelected={(option, isSelected) => props.onGroupIdChange(isSelected ? option : '')}
            isMultiSelect={false}
            allowNewValues
            isLoading={props.consumerGroupsLoading}
            shouldAutocomplete
        />
    )
}

interface ConsumerGroupCardProps {
    selectedTopic: string | null
    consumerGroups: string[]
    consumerGroupsLoading: boolean
    supportsConsumerGroupLookup: boolean
}

function ConsumerOffsetsCard(props: ConsumerGroupCardProps) {
    const [groupIdField, setGroupIdField] = useState('')
    const [topicPartitionOffsets, setTopicPartitionOffsets] = useState<TopicPartitionOffset[]>([])
    const [lastRecordOffsets, setLastRecordOffsets] = useState<TopicPartitionOffset[]>([])
    const requestId = useRef(0)

    useEffect(() => {
        requestId.current += 1
        setGroupIdField('')
        setTopicPartitionOffsets([])
        setLastRecordOffsets([])
    }, [props.selectedTopic])

    function handleGroupIdChange(groupId: string) {
        requestId.current += 1
        setGroupIdField(groupId)
        setTopicPartitionOffsets([])
        setLastRecordOffsets([])
    }

    function handleHentConsumerOffsets() {
        if (props.selectedTopic == null) {
            errorToast('Please select a topic from the sidebar')
            return
        }

        const request: GetConsumerOffsetsRequest = {groupId: groupIdField, topicName: props.selectedTopic}
        const lastRecordOffsetsRequest: GetLastRecordOffsetsRequest = {topicName: props.selectedTopic}
        // Topic- eller gruppebytte gjør svar fra tidligere forespørsler ugyldige.
        const currentRequestId = ++requestId.current
        setTopicPartitionOffsets([])
        setLastRecordOffsets([])

        // Vi trenger både consumerens offset og topicens log-end offset for å finne lag.
        Promise.all([getConsumerOffsets(request), getLastRecordOffsets(lastRecordOffsetsRequest)])
            .then(([consumerOffsets, lastRecordOffsets]) => {
                if (currentRequestId !== requestId.current) {
                    return
                }

                if (consumerOffsets.length === 0) {
                    warningToast('Fant ingen offsets tilhørende konsumeren for topicen')
                } else {
                    setTopicPartitionOffsets(consumerOffsets)
                    setLastRecordOffsets(lastRecordOffsets)
                }
            })
            .catch(() => {
                if (currentRequestId === requestId.current) {
                    errorToast('Klarte ikke å hente consumer offsets')
                }
            })
    }

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

function LastRecordOffsetCard(props: { selectedTopic: string | null }) {
    const [lastRecordOffsets, setLastRecordOffsets] = useState<TopicPartitionOffset[]>([])

    useEffect(() => {
        setLastRecordOffsets([])
    }, [props.selectedTopic])

    function handleFetchLastRecordOffsets() {
        if (props.selectedTopic == null) {
            errorToast('Please select a topic from the sidebar')
            return
        }

        const request: GetLastRecordOffsetsRequest = {topicName: props.selectedTopic}

        getLastRecordOffsets(request)
            .then(offsets => setLastRecordOffsets(offsets))
            .catch(() => errorToast('Klarte ikke å hente siste record offset'))
    }

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

function SetConsumerOffsetCard(props: ConsumerGroupCardProps) {
    const [groupIdField, setGroupIdField] = useState('')
    const [topicPartitionField, setTopicPartitionField] = useState('0')
    const [offsetField, setOffsetField] = useState('0')

    useEffect(() => {
        setGroupIdField('')
    }, [props.selectedTopic])

    function handleSetConsumerOffset() {
        if (props.selectedTopic == null) {
            errorToast('Please select a topic from the sidebar')
            return
        }

        const topicPartition = parseNonNegativeSafeInteger(topicPartitionField)
        const offset = parseNonNegativeSafeInteger(offsetField)
        if (topicPartition == null || offset == null) {
            errorToast('Please enter a valid non-negative partition and offset')
            return
        }

        const request: SetConsumerOffsetRequest = {
            topicName: props.selectedTopic,
            topicPartition,
            offset,
            groupId: groupIdField
        }

        setConsumerOffset(request)
            .then(() => successToast('Consumer offset har blitt endret'))
            .catch(() => errorToast('Klarte ikke å sette consumer offset'))
    }

    return (
        <Card title="Set consumer offset" className="set-consumer-offset-card" innholdClassName="card__content">
            <BodyShort spacing>
                Setter offset til en consumer for en topic+partisjon. Det er viktig å vite at selv om offsetet blir
                endret, så vil ikke consumere plukke opp endringen i offset før de er startet på nytt. Hvis en consumer
                committer et nytt offset før den har blitt startet på nytt og fått hentet inn endringen, så vil den
                overskrive offsetet fra kafka-manager.
            </BodyShort>
            <SelectedTopic selectedTopic={props.selectedTopic}/>
            <TextField
                label="Topic partition (first partition starts at 0)"
                type="number"
                value={topicPartitionField}
                onChange={e => setTopicPartitionField(e.target.value)}
            />
            <ConsumerGroupField
                groupId={groupIdField}
                onGroupIdChange={setGroupIdField}
                consumerGroups={props.consumerGroups}
                consumerGroupsLoading={props.consumerGroupsLoading}
                supportsConsumerGroupLookup={props.supportsConsumerGroupLookup}
            />
            <VStack gap="space-12">
                <TextField
                    label="Offset"
                    type="number"
                    value={offsetField}
                    onChange={e => setOffsetField(e.target.value)}
                />
                <Button
                    onClick={handleSetConsumerOffset}
                    variant="tertiary"
                    disabled={!props.selectedTopic || groupIdField.trim().length === 0}
                >
                    Set offset
                </Button>
            </VStack>
        </Card>
    )
}

function ReadFromTopicCard(props: { selectedTopic: string | null }) {
    const [isLoading, setIsLoading] = useState<boolean>(false)
    const [partitionMode, setPartitionMode] = useState<'all' | 'specific'>('all')
    const [topicPartitionField, setTopicPartitionField] = useState('0')
    const [fetchFromField, setFetchFromField] = useState<'BEGINNING' | 'END' | 'OFFSET'>('END')
    const [fromOffsetField, setFromOffsetField] = useState('0')
    const [maxRecordsField, setMaxRecordsField] = useState('50')
    const [keyValueFilterField, setKeyValueFilterField] = useState('')

    const [clickedRecord, setClickedRecord] = useState<KafkaRecord | null>(null)
    const [recordsFromTopic, setRecordsFromTopic] = useState<KafkaRecord[]>([])

    const [startTimeMs, setStartTimeMs] = useState<number | null>(null)
    const [timeTakenMs, setTimeTakenMs] = useState<number>(0)
    const requestId = useRef(0)

    async function handleReadFromTopic() {
        if (props.selectedTopic == null) {
            errorToast('Please select a topic from the sidebar')
            return
        }

        const maxRecords = parseNonNegativeSafeInteger(maxRecordsField)
        if (maxRecords == null || maxRecords > 100) {
            errorToast('Max records must be an integer between 0 and 100')
            return
        }

        let topicPartition = 0
        if (partitionMode === 'specific' || fetchFromField === 'END') {
            const parsedTopicPartition = parseNonNegativeSafeInteger(topicPartitionField)
            if (parsedTopicPartition == null) {
                errorToast('Please enter a valid non-negative topic partition')
                return
            }
            topicPartition = parsedTopicPartition
        }

        let explicitOffset = 0
        if (fetchFromField === 'OFFSET') {
            const parsedOffset = parseNonNegativeSafeInteger(fromOffsetField)
            if (parsedOffset == null) {
                errorToast('Please enter a valid non-negative offset')
                return
            }
            explicitOffset = parsedOffset
        }

        const currentRequestId = ++requestId.current
        const topicName = props.selectedTopic

        setRecordsFromTopic([])
        setClickedRecord(null)
        setStartTimeMs(Date.now())
        setTimeTakenMs(0)
        setIsLoading(true)

        let fetchFromOffset

        if (fetchFromField === 'BEGINNING') {
            fetchFromOffset = 0
        } else if (fetchFromField === 'END') {
            try {
                const lastRecordOffset = (await getLastRecordOffset({topicName, topicPartition}))
                    .latestRecordOffset
                if (currentRequestId !== requestId.current) {
                    return
                }

                fetchFromOffset = lastRecordOffset - maxRecords
            } catch {
                if (currentRequestId === requestId.current) {
                    setStartTimeMs(null)
                    setTimeTakenMs(0)
                    setIsLoading(false)
                    errorToast('Klarte ikke å hente siste record offset')
                }
                return
            }
        } else {
            fetchFromOffset = explicitOffset
        }

        const request: ReadFromTopicRequest = {
            topicName,
            topicAllPartitions: partitionMode === 'all',
            topicPartition,
            fromOffset: fetchFromOffset,
            maxRecords,
            filterText: keyValueFilterField
        }

        try {
            const records = await readFromTopic(request)
            if (currentRequestId !== requestId.current) {
                return
            }

            if (records.length === 0) {
                warningToast('Could not find any records in the topic+partition for the given offset')
            } else {
                setRecordsFromTopic(records)
            }
        } catch {
            if (currentRequestId === requestId.current) {
                errorToast('Klarte ikke å lese meldinger fra valgt topic')
            }
        } finally {
            if (currentRequestId === requestId.current) {
                setStartTimeMs(null)
                setTimeTakenMs(0)
                setIsLoading(false)
            }
        }
    }

    useLayoutEffect(() => {
        requestId.current += 1
        setRecordsFromTopic([])
        setClickedRecord(null)
        setStartTimeMs(null)
        setTimeTakenMs(0)
        setIsLoading(false)

        return () => {
            requestId.current += 1
        }
    }, [props.selectedTopic])

    useEffect(() => {
        if (startTimeMs == null) {
            return
        }

        const interval = window.setInterval(() => setTimeTakenMs(Date.now() - startTimeMs), 100)
        return () => window.clearInterval(interval)
    }, [startTimeMs])

    return (
        <Card
            title="Read records from topic"
            className="read-from-topic-card very-large-card center-horizontal"
            innholdClassName="card__content"
        >
            <BodyShort spacing>
                Leser meldinger fra en topic+partisjon. Trykk på en av meldingene for å se mer detaljert informasjon
            </BodyShort>

            <SelectedTopic selectedTopic={props.selectedTopic}/>

            <div className="toggle-group-container">
                <div className="toggle-group-wrapper">
                    <Label className="toggle-group-label">Topic partitions</Label>
                    <ToggleGroup
                        value={partitionMode}
                        onChange={value => setPartitionMode(value as 'all' | 'specific')}
                        size="small"
                    >
                        <ToggleGroup.Item value="all">All</ToggleGroup.Item>
                        <ToggleGroup.Item value="specific">Specific</ToggleGroup.Item>
                    </ToggleGroup>
                </div>

                <div className="toggle-group-wrapper">
                    <Label className="toggle-group-label">Fetch from</Label>
                    <ToggleGroup
                        value={fetchFromField}
                        onChange={value => setFetchFromField(value as 'BEGINNING' | 'END' | 'OFFSET')}
                        size="small"
                    >
                        <ToggleGroup.Item value="BEGINNING">Beginning</ToggleGroup.Item>
                        <ToggleGroup.Item value="END">End</ToggleGroup.Item>
                        <ToggleGroup.Item value="OFFSET">Offset</ToggleGroup.Item>
                    </ToggleGroup>
                </div>
            </div>

            {partitionMode === 'specific' && (
                <TextField
                    label="Topic partition number (first partition starts at 0)"
                    type="number"
                    value={topicPartitionField}
                    onChange={e => setTopicPartitionField(e.target.value)}
                />
            )}

            {fetchFromField === 'OFFSET' && (
                <TextField
                    label="From offset"
                    type="number"
                    value={fromOffsetField}
                    onChange={e => setFromOffsetField(e.target.value)}
                />
            )}

            <TextField
                label="Max records (maximum of records that will be returned, max=100)"
                type="number"
                value={maxRecordsField}
                onChange={e => setMaxRecordsField(e.target.value)}
            />

            <VStack gap="space-12">
                <TextField
                    label="Key/value filter (tip: max=1 can be used to reduce waiting)"
                    value={keyValueFilterField}
                    onChange={e => setKeyValueFilterField(e.target.value)}
                />
                <Button onClick={handleReadFromTopic} variant="tertiary" disabled={!props.selectedTopic}>
                    Fetch
                </Button>
            </VStack>

            {isLoading && recordsFromTopic.length === 0 ? (
                <div className="read-from-topic-card__loader">
                    <Loader size="2xlarge"/>
                    <BodyShort className="read-from-topic-card__loader-timer">{toTimerStr(timeTakenMs)}</BodyShort>
                </div>
            ) : null}

            {recordsFromTopic.length > 0 ? (
                <table className="tabell">
                    <thead>
                    <tr>
                        <th>Partition</th>
                        <th>Offset</th>
                        <th>Key</th>
                        <th>Value</th>
                        <th>Details</th>
                    </tr>
                    </thead>
                    <tbody>
                    {recordsFromTopic
                        .sort((r1, r2) => r1.offset - r2.offset)
                        .map(record => {
                            return (
                                <tr key={`${record.partition}-${record.offset}`}>
                                    <td>{record.partition}</td>
                                    <td>{record.offset}</td>
                                    <td>{record.key || 'NO_KEY'}</td>
                                    <td className="kafka-record-value">{record.value}</td>
                                    <td>
                                        <Button
                                            variant="tertiary"
                                            onClick={() => setClickedRecord(record)}
                                            aria-label={`View record at partition ${record.partition}, offset ${record.offset}`}
                                        >
                                            View
                                        </Button>
                                    </td>
                                </tr>
                            )
                        })}
                    </tbody>
                </table>
            ) : null}
            <Modal
                open={clickedRecord !== null}
                onClose={() => setClickedRecord(null)}
                aria-label="kafka record content"
            >
                <Modal.Body>
                    {clickedRecord && (
                        <Suspense fallback={<PageSpinner/>}>
                            <KafkaRecordModalContent record={clickedRecord}/>
                        </Suspense>
                    )}
                </Modal.Body>
            </Modal>
        </Card>
    )
}
