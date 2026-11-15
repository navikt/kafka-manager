import {useEffect, useState} from 'react'
import {errorToast, successToast} from '../../utils/toast-utils'
import {Card} from '../../component/card/card'
import {BodyShort, Button, TextField, VStack} from '@navikt/ds-react'
import {setConsumerOffset, SetConsumerOffsetRequest} from '../../api'
import {parseNonNegativeSafeInteger} from '../../utils'
import {SelectedTopic} from './SelectedTopic'
import {ConsumerGroupCardProps, ConsumerGroupField} from './ConsumerGroupField'

export const SetConsumerOffsetCard = (props: ConsumerGroupCardProps) => {
    const [groupIdField, setGroupIdField] = useState('')
    const [topicPartitionField, setTopicPartitionField] = useState('0')
    const [offsetField, setOffsetField] = useState('0')

    useEffect(() => {
        setGroupIdField('')
    }, [props.selectedTopic])

    const handleSetConsumerOffset = () => {
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
