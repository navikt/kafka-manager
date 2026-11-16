import {cleanup, fireEvent, render, screen, waitFor} from '@testing-library/react'
import {afterEach, describe, expect, it, vi} from 'vitest'
import {setConsumerOffset} from '../../api'
import {successToast} from '../../utils/toast-utils'
import {SetConsumerOffsetCard} from './SetConsumerOffsetCard'

vi.mock('../../api', () => ({
    setConsumerOffset: vi.fn()
}))

vi.mock('../../utils/toast-utils', () => ({
    errorToast: vi.fn(),
    successToast: vi.fn()
}))

vi.mock('@navikt/ds-react', async (importOriginal) => {
    const actual = await importOriginal<typeof import('@navikt/ds-react')>()
    const {UnsafeComboboxMock} = await import('./UnsafeComboboxMock')

    return {
        ...actual,
        UNSAFE_Combobox: UnsafeComboboxMock
    }
})

const props = {
    selectedTopic: 'aiven-topic',
    consumerGroups: ['aiven-topic-group'],
    consumerGroupsLoading: false,
    supportsConsumerGroupLookup: true
}

describe('SetConsumerOffsetCard layout', () => {
    afterEach(() => {
        cleanup()
        vi.clearAllMocks()
    })

    it('renders the partition and offset fields with the action disabled until a group is selected', () => {
        render(<SetConsumerOffsetCard {...props}/>)

        expect(screen.getByText('Set consumer offset')).toBeTruthy()
        expect(screen.getByLabelText('Topic partition (first partition starts at 0)')).toBeTruthy()
        expect(screen.getByLabelText('Offset')).toBeTruthy()
        expect((screen.getByRole('button', {name: 'Set offset'}) as HTMLButtonElement).disabled).toBe(true)
    })
})

describe('SetConsumerOffsetCard interactions', () => {
    afterEach(() => {
        cleanup()
        vi.clearAllMocks()
    })

    it('sets the requested offset and shows success feedback', async () => {
        vi.mocked(setConsumerOffset).mockResolvedValue(undefined)
        render(<SetConsumerOffsetCard {...props}/>)

        fireEvent.change(screen.getByLabelText('Consumer group id'), {
            target: {value: 'aiven-topic-group'}
        })
        fireEvent.change(screen.getByLabelText('Topic partition (first partition starts at 0)'), {
            target: {value: '2'}
        })
        fireEvent.change(screen.getByLabelText('Offset'), {target: {value: '42'}})
        fireEvent.click(screen.getByRole('button', {name: 'Set offset'}))

        await waitFor(() => {
            expect(setConsumerOffset).toHaveBeenCalledWith({
                topicName: 'aiven-topic',
                topicPartition: 2,
                offset: 42,
                groupId: 'aiven-topic-group'
            })
            expect(successToast).toHaveBeenCalledWith('Consumer offset har blitt endret')
        })
    })
})
