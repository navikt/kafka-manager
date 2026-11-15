import {useEffect, useState} from 'react'
import {useQuery} from '@tanstack/react-query'
import {errorToast} from '../../utils/toast-utils'
import {UNSAFE_Combobox} from '@navikt/ds-react'
import {getAvailableTopics, getConsumerGroups} from '../../api'
import './kafka-admin.css'
import {PageSpinner} from '../../component/page-spinner/page-spinner'
import {DeleteRecordsCard} from './DeleteRecordsCard'
import {ConsumerOffsetsCard} from './ConsumerOffsetsCard'
import {LastRecordOffsetCard} from './LastRecordOffsetCard'
import {ReadFromTopicCard} from './ReadFromTopicCard'
import {SetConsumerOffsetCard} from './SetConsumerOffsetCard'

export const KafkaAdmin = () => {
    const [selectedTopic, setSelectedTopic] = useState<string | null>(null)
    const availableTopicsQuery = useQuery({
        queryKey: ['available-topics'],
        queryFn: getAvailableTopics,
        retry: false,
        refetchOnWindowFocus: false
    })

    useEffect(() => {
        if (availableTopicsQuery.error && !availableTopicsQuery.isFetching) {
            errorToast('Unable to load available topics')
        }
    }, [availableTopicsQuery.error, availableTopicsQuery.isFetching])

    const availableTopics = availableTopicsQuery.data ?? []
    const selectedTopicDetails = availableTopics.find(availableTopic => availableTopic.name === selectedTopic)
    const supportsConsumerGroupLookup = selectedTopicDetails?.supportsRecordDeletion ?? false
    const consumerGroupsQuery = useQuery({
        queryKey: ['consumer-groups', selectedTopic],
        queryFn: () => {
            if (selectedTopic == null) {
                throw new Error('A topic must be selected to load consumer groups')
            }

            return getConsumerGroups({topicName: selectedTopic})
        },
        enabled: selectedTopic != null && supportsConsumerGroupLookup,
        retry: false,
        refetchOnWindowFocus: false
    })

    useEffect(() => {
        if (consumerGroupsQuery.error && !consumerGroupsQuery.isFetching) {
            errorToast('Unable to load consumer groups')
        }
    }, [consumerGroupsQuery.error, consumerGroupsQuery.isFetching])

    if (availableTopicsQuery.isLoading) {
        return <PageSpinner/>
    }

    const sortedTopics = [...availableTopics].sort((a, b) => a.name.localeCompare(b.name))
    const consumerGroupOptions = consumerGroupsQuery.data ?? []
    const isConsumerGroupsLoading = consumerGroupsQuery.isFetching

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
