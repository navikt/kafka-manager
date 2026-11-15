import {useEffect, useLayoutEffect, useRef, useState} from 'react'
import {errorToast, warningToast} from '../../utils/toast-utils'
import {Card} from '../../component/card/card'
import {getLastRecordOffset, KafkaRecord, readFromTopic, ReadFromTopicRequest} from '../../api'
import {parseNonNegativeSafeInteger} from '../../utils'
import {SelectedTopic} from './SelectedTopic'
import {ReadFromTopicForm, ReadFromTopicFormValues} from './ReadFromTopicForm'
import {ReadFromTopicResults} from './ReadFromTopicResults'

export const ReadFromTopicCard = (props: { selectedTopic: string | null }) => {
    const [isLoading, setIsLoading] = useState<boolean>(false)
    const [recordsFromTopic, setRecordsFromTopic] = useState<KafkaRecord[]>([])

    const [startTimeMs, setStartTimeMs] = useState<number | null>(null)
    const [timeTakenMs, setTimeTakenMs] = useState<number>(0)
    const requestId = useRef(0)

    const handleReadFromTopic = async (options: ReadFromTopicFormValues) => {
        if (props.selectedTopic == null) {
            errorToast('Please select a topic from the sidebar')
            return
        }

        const maxRecords = parseNonNegativeSafeInteger(options.maxRecordsField)
        if (maxRecords == null || maxRecords > 100) {
            errorToast('Max records must be an integer between 0 and 100')
            return
        }

        let topicPartition = 0
        if (options.partitionMode === 'specific' || options.fetchFromField === 'END') {
            const parsedTopicPartition = parseNonNegativeSafeInteger(options.topicPartitionField)
            if (parsedTopicPartition == null) {
                errorToast('Please enter a valid non-negative topic partition')
                return
            }
            topicPartition = parsedTopicPartition
        }

        let explicitOffset = 0
        if (options.fetchFromField === 'OFFSET') {
            const parsedOffset = parseNonNegativeSafeInteger(options.fromOffsetField)
            if (parsedOffset == null) {
                errorToast('Please enter a valid non-negative offset')
                return
            }
            explicitOffset = parsedOffset
        }

        const currentRequestId = ++requestId.current
        const topicName = props.selectedTopic

        setRecordsFromTopic([])
        setStartTimeMs(Date.now())
        setTimeTakenMs(0)
        setIsLoading(true)

        let fetchFromOffset

        if (options.fetchFromField === 'BEGINNING') {
            fetchFromOffset = 0
        } else if (options.fetchFromField === 'END') {
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
            topicAllPartitions: options.partitionMode === 'all',
            topicPartition,
            fromOffset: fetchFromOffset,
            maxRecords,
            filterText: options.keyValueFilterField
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
            <SelectedTopic selectedTopic={props.selectedTopic}/>
            <ReadFromTopicForm selectedTopic={props.selectedTopic} onSubmit={handleReadFromTopic}/>
            <ReadFromTopicResults
                selectedTopic={props.selectedTopic}
                isLoading={isLoading}
                records={recordsFromTopic}
                timeTakenMs={timeTakenMs}
            />
        </Card>
    )
}
