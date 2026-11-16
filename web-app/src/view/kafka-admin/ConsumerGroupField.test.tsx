import {cleanup, fireEvent, render, screen} from '@testing-library/react'
import {afterEach, describe, expect, it, vi} from 'vitest'
import {ConsumerGroupField} from './ConsumerGroupField'

vi.mock('@navikt/ds-react', async (importOriginal) => {
    const actual = await importOriginal<typeof import('@navikt/ds-react')>()
    const {UnsafeComboboxMock} = await import('./UnsafeComboboxMock')

    return {
        ...actual,
        UNSAFE_Combobox: UnsafeComboboxMock
    }
})

describe('ConsumerGroupField layout', () => {
    afterEach(cleanup)

    it('renders a text field when consumer groups are not available', () => {
        render(
            <ConsumerGroupField
                groupId=""
                onGroupIdChange={vi.fn()}
                consumerGroups={[]}
                consumerGroupsLoading={false}
                supportsConsumerGroupLookup={false}
            />
        )

        expect(screen.getByLabelText('Consumer group id')).toBeTruthy()
    })

    it('renders a group selector when lookup is supported', () => {
        render(
            <ConsumerGroupField
                groupId=""
                onGroupIdChange={vi.fn()}
                consumerGroups={['known-group']}
                consumerGroupsLoading={false}
                supportsConsumerGroupLookup={true}
            />
        )

        expect(screen.getByRole('option', {name: 'known-group'})).toBeTruthy()
    })
})

describe('ConsumerGroupField interactions', () => {
    afterEach(cleanup)

    it('forwards group changes from the text field and selector', () => {
        const onGroupIdChange = vi.fn()
        const props = {
            groupId: '',
            onGroupIdChange,
            consumerGroups: ['known-group'],
            consumerGroupsLoading: false
        }
        const {rerender} = render(
            <ConsumerGroupField {...props} supportsConsumerGroupLookup={false}/>
        )

        fireEvent.change(screen.getByLabelText('Consumer group id'), {
            target: {value: 'custom-group'}
        })
        expect(onGroupIdChange).toHaveBeenLastCalledWith('custom-group')

        rerender(<ConsumerGroupField {...props} supportsConsumerGroupLookup={true}/>)
        fireEvent.change(screen.getByLabelText('Consumer group id'), {
            target: {value: 'known-group'}
        })
        expect(onGroupIdChange).toHaveBeenLastCalledWith('known-group')
    })
})
