import {lazy, Suspense, useLayoutEffect, useState} from 'react'
import {BodyShort, Button, Loader, Modal} from '@navikt/ds-react'
import {KafkaRecord} from '../../api'
import {PageSpinner} from '../../component/page-spinner/page-spinner'
import {toTimerStr} from '../../utils/date-utils'

const KafkaRecordModalContent = lazy(() =>
    import('./KafkaRecordModalContent').then(({KafkaRecordModalContent}) => ({
        default: KafkaRecordModalContent
    }))
)

interface ReadFromTopicResultsProps {
    selectedTopic: string | null
    isLoading: boolean
    records: KafkaRecord[]
    timeTakenMs: number
}

export const ReadFromTopicResults = (props: ReadFromTopicResultsProps) => {
    const [clickedRecord, setClickedRecord] = useState<KafkaRecord | null>(null)

    useLayoutEffect(() => {
        setClickedRecord(null)
    }, [props.selectedTopic])

    return (
        <>
            {props.isLoading && props.records.length === 0 ? (
                <div className="read-from-topic-card__loader">
                    <Loader size="2xlarge"/>
                    <BodyShort className="read-from-topic-card__loader-timer">
                        {toTimerStr(props.timeTakenMs)}
                    </BodyShort>
                </div>
            ) : null}

            {props.records.length > 0 ? (
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
                    {[...props.records]
                        .sort((r1, r2) => r1.offset - r2.offset)
                        .map(record => (
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
                        ))}
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
        </>
    )
}
