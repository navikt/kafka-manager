import {TextField, UNSAFE_Combobox} from '@navikt/ds-react'

export interface ConsumerGroupFieldProps {
    groupId: string
    onGroupIdChange: (groupId: string) => void
    consumerGroups: string[]
    consumerGroupsLoading: boolean
    supportsConsumerGroupLookup: boolean
}

export interface ConsumerGroupCardProps {
    selectedTopic: string | null
    consumerGroups: string[]
    consumerGroupsLoading: boolean
    supportsConsumerGroupLookup: boolean
}

export const ConsumerGroupField = (props: ConsumerGroupFieldProps) => {
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
