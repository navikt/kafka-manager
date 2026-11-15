import {useState} from 'react'
import {BodyShort, Button, Label, TextField, ToggleGroup, VStack} from '@navikt/ds-react'

export interface ReadFromTopicFormValues {
    partitionMode: 'all' | 'specific'
    topicPartitionField: string
    fetchFromField: 'BEGINNING' | 'END' | 'OFFSET'
    fromOffsetField: string
    maxRecordsField: string
    keyValueFilterField: string
}

interface ReadFromTopicFormProps {
    selectedTopic: string | null
    onSubmit: (values: ReadFromTopicFormValues) => void
}

export const ReadFromTopicForm = (props: ReadFromTopicFormProps) => {
    const [partitionMode, setPartitionMode] = useState<ReadFromTopicFormValues['partitionMode']>('all')
    const [topicPartitionField, setTopicPartitionField] = useState('0')
    const [fetchFromField, setFetchFromField] = useState<ReadFromTopicFormValues['fetchFromField']>('END')
    const [fromOffsetField, setFromOffsetField] = useState('0')
    const [maxRecordsField, setMaxRecordsField] = useState('50')
    const [keyValueFilterField, setKeyValueFilterField] = useState('')

    const handleSubmit = () => {
        props.onSubmit({
            partitionMode,
            topicPartitionField,
            fetchFromField,
            fromOffsetField,
            maxRecordsField,
            keyValueFilterField
        })
    }

    return (
        <>
            <BodyShort spacing>
                Leser meldinger fra en topic+partisjon. Trykk på en av meldingene for å se mer detaljert informasjon
            </BodyShort>
            <div className="toggle-group-container">
                <div className="toggle-group-wrapper">
                    <Label className="toggle-group-label">Topic partitions</Label>
                    <ToggleGroup
                        value={partitionMode}
                        onChange={value => setPartitionMode(value as ReadFromTopicFormValues['partitionMode'])}
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
                        onChange={value => setFetchFromField(value as ReadFromTopicFormValues['fetchFromField'])}
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
                <Button onClick={handleSubmit} variant="tertiary" disabled={!props.selectedTopic}>
                    Fetch
                </Button>
            </VStack>
        </>
    )
}
