import {QueryClient, QueryClientProvider} from '@tanstack/react-query'
import {cleanup, fireEvent, render, screen, waitFor} from '@testing-library/react'
import {afterEach, describe, expect, it, vi} from 'vitest'
import {getConsumerOffsets, getLastRecordOffsets} from '../../api'
import {errorToast, warningToast} from '../../utils/toast-utils'
import {ConsumerOffsetsCard} from './ConsumerOffsetsCard'

vi.mock('../../api', () => ({
    getConsumerOffsets: vi.fn(),
    getLastRecordOffsets: vi.fn()
}))

vi.mock('../../utils/toast-utils', () => ({
    errorToast: vi.fn(),
    warningToast: vi.fn()
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

const renderCard = (cardProps = props) => {
    const queryClient = new QueryClient({
        defaultOptions: {queries: {retry: false}}
    })
    const view = render(
        <QueryClientProvider client={queryClient}>
            <ConsumerOffsetsCard {...cardProps}/>
        </QueryClientProvider>
    )
    return {queryClient, ...view}
}

describe('ConsumerOffsetsCard layout', () => {
    afterEach(() => {
        cleanup()
        vi.clearAllMocks()
    })

    it('renders the group field and a disabled fetch button until a group is selected', () => {
        const {queryClient} = renderCard()

        expect(screen.getByText('Get consumer offsets')).toBeTruthy()
        expect(screen.getByLabelText('Consumer group id')).toBeTruthy()
        expect((screen.getByRole('button', {name: 'Fetch'}) as HTMLButtonElement).disabled).toBe(true)
        queryClient.clear()
    })
})

describe('ConsumerOffsetsCard interactions', () => {
    afterEach(() => {
        cleanup()
        vi.clearAllMocks()
    })

    it('fetches consumer and log-end offsets for the selected group', async () => {
        vi.mocked(getConsumerOffsets).mockResolvedValue([
            {topicName: 'aiven-topic', topicPartition: 0, offset: 5}
        ])
        vi.mocked(getLastRecordOffsets).mockResolvedValue([
            {topicName: 'aiven-topic', topicPartition: 0, offset: 8}
        ])
        const {queryClient} = renderCard()

        fireEvent.change(screen.getByLabelText('Consumer group id'), {
            target: {value: 'aiven-topic-group'}
        })
        fireEvent.click(screen.getByRole('button', {name: 'Fetch'}))

        expect(await screen.findByText('5')).toBeTruthy()
        expect(getConsumerOffsets).toHaveBeenCalledWith({
            topicName: 'aiven-topic',
            groupId: 'aiven-topic-group'
        })
        expect(getLastRecordOffsets).toHaveBeenCalledWith({topicName: 'aiven-topic'})
        queryClient.clear()
    })

    it('does not report a cached error while the selected group is being refetched', async () => {
        let resolveCachedRefresh:
            | ((offsets: Awaited<ReturnType<typeof getConsumerOffsets>>) => void)
            | undefined
        vi.mocked(getConsumerOffsets)
            .mockResolvedValueOnce([{topicName: 'aiven-topic', topicPartition: 0, offset: 5}])
            .mockRejectedValueOnce(new Error('Refresh failed'))
            .mockResolvedValueOnce([{topicName: 'aiven-topic', topicPartition: 0, offset: 6}])
            .mockImplementationOnce(
                () =>
                    new Promise(resolve => {
                        resolveCachedRefresh = resolve
                    })
            )
        vi.mocked(getLastRecordOffsets).mockResolvedValue([
            {topicName: 'aiven-topic', topicPartition: 0, offset: 10}
        ])
        const {queryClient} = renderCard({
            ...props,
            consumerGroups: ['group-a', 'group-b']
        })
        const groupSelector = screen.getByLabelText('Consumer group id')
        const fetchButton = screen.getByRole('button', {name: 'Fetch'})

        fireEvent.change(groupSelector, {target: {value: 'group-a'}})
        fireEvent.click(fetchButton)
        expect(await screen.findByText('5')).toBeTruthy()

        fireEvent.click(fetchButton)
        await waitFor(() => expect(errorToast).toHaveBeenCalledTimes(1))

        fireEvent.change(groupSelector, {target: {value: 'group-b'}})
        fireEvent.click(fetchButton)
        expect(await screen.findByText('6')).toBeTruthy()

        fireEvent.change(groupSelector, {target: {value: 'group-a'}})
        fireEvent.click(fetchButton)
        await waitFor(() => expect(getConsumerOffsets).toHaveBeenCalledTimes(4))
        expect(errorToast).toHaveBeenCalledTimes(1)

        if (resolveCachedRefresh == null) {
            throw new Error('The cached consumer-offset refresh did not start')
        }
        resolveCachedRefresh([{topicName: 'aiven-topic', topicPartition: 0, offset: 7}])

        expect(await screen.findByText('7')).toBeTruthy()
        expect(errorToast).toHaveBeenCalledTimes(1)
        queryClient.clear()
    })

    it('warns after each completed empty fetch, not while a refresh is pending', async () => {
        let resolveSecondRequest:
            | ((offsets: Awaited<ReturnType<typeof getConsumerOffsets>>) => void)
            | undefined
        vi.mocked(getConsumerOffsets)
            .mockResolvedValueOnce([])
            .mockImplementationOnce(
                () =>
                    new Promise(resolve => {
                        resolveSecondRequest = resolve
                    })
            )
        const {queryClient} = renderCard()

        fireEvent.change(screen.getByLabelText('Consumer group id'), {
            target: {value: 'aiven-topic-group'}
        })
        fireEvent.click(screen.getByRole('button', {name: 'Fetch'}))
        await waitFor(() => expect(warningToast).toHaveBeenCalledTimes(1))

        fireEvent.click(screen.getByRole('button', {name: 'Fetch'}))
        await waitFor(() => expect(getConsumerOffsets).toHaveBeenCalledTimes(2))
        expect(warningToast).toHaveBeenCalledTimes(1)

        if (resolveSecondRequest == null) {
            throw new Error('The second consumer-offset request did not start')
        }
        resolveSecondRequest([])

        await waitFor(() => expect(warningToast).toHaveBeenCalledTimes(2))
        queryClient.clear()
    })

    it('waits for a refresh before warning when returning to cached empty results', async () => {
        let resolveRefresh:
            | ((offsets: Awaited<ReturnType<typeof getConsumerOffsets>>) => void)
            | undefined
        vi.mocked(getConsumerOffsets)
            .mockResolvedValueOnce([])
            .mockResolvedValueOnce([])
            .mockImplementationOnce(
                () =>
                    new Promise(resolve => {
                        resolveRefresh = resolve
                    })
            )
        const {queryClient} = renderCard({
            ...props,
            consumerGroups: ['group-a', 'group-b']
        })
        const groupSelector = screen.getByLabelText('Consumer group id')
        const fetchButton = screen.getByRole('button', {name: 'Fetch'})

        fireEvent.change(groupSelector, {target: {value: 'group-a'}})
        fireEvent.click(fetchButton)
        await waitFor(() => expect(warningToast).toHaveBeenCalledTimes(1))

        fireEvent.change(groupSelector, {target: {value: 'group-b'}})
        fireEvent.click(fetchButton)
        await waitFor(() => expect(warningToast).toHaveBeenCalledTimes(2))

        fireEvent.change(groupSelector, {target: {value: 'group-a'}})
        fireEvent.click(fetchButton)
        await waitFor(() => expect(getConsumerOffsets).toHaveBeenCalledTimes(3))
        expect(warningToast).toHaveBeenCalledTimes(2)

        if (resolveRefresh == null) {
            throw new Error('The cached consumer-offset refresh did not start')
        }
        resolveRefresh([])

        await waitFor(() => expect(warningToast).toHaveBeenCalledTimes(3))
        queryClient.clear()
    })
})
