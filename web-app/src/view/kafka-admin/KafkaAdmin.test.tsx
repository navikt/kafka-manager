import {QueryClient, QueryClientProvider} from '@tanstack/react-query'
import {cleanup, fireEvent, render, screen, waitFor} from '@testing-library/react'
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {
    getAvailableTopics,
    getConsumerGroups,
    getConsumerOffsets,
    getLastRecordOffsets
} from '../../api'
import {errorToast, warningToast} from '../../utils/toast-utils'
import { KafkaAdmin } from './KafkaAdmin'

vi.mock('../../api', () => ({
    deleteConsumerRecords: vi.fn(),
    getAvailableTopics: vi.fn(),
    getConsumerGroups: vi.fn(),
    getConsumerOffsets: vi.fn(),
    getLastRecordOffset: vi.fn(),
    getLastRecordOffsets: vi.fn(),
    readFromTopic: vi.fn(),
    setConsumerOffset: vi.fn()
}))

vi.mock('../../utils/toast-utils', () => ({
    errorToast: vi.fn(),
    successToast: vi.fn(),
    warningToast: vi.fn()
}))

vi.mock('@navikt/ds-react', async (importOriginal) => {
    const actual = await importOriginal<typeof import('@navikt/ds-react')>()
    const {UnsafeComboboxMock} = await import('./UnsafeComboboxMock')

    // noinspection JSUnusedGlobalSymbols
    return {
        ...actual,
        UNSAFE_Combobox: UnsafeComboboxMock
    }
})

const availableTopics = [
    {name: 'on-prem-topic', supportsRecordDeletion: false},
    {name: 'aiven-topic', supportsRecordDeletion: true},
    {name: 'another-aiven-topic', supportsRecordDeletion: true}
]

const queryClients: QueryClient[] = []

const renderKafkaAdmin = () => {
    const queryClient = new QueryClient({
        defaultOptions: {queries: {gcTime: Infinity, retry: false}}
    })
    queryClients.push(queryClient)

    return render(
        <QueryClientProvider client={queryClient}>
            <KafkaAdmin/>
        </QueryClientProvider>
    )
}

const selectConsumerGroup = async (topicName: string, groupId: string) => {
    const topicSelector = await screen.findByLabelText('Select Kafka Topic')
    fireEvent.change(topicSelector, {target: {value: topicName}})
    await screen.findAllByRole('option', {name: `${topicName}-group`})
    fireEvent.change(screen.getAllByLabelText('Consumer group id')[0], {
        target: {value: groupId}
    })
}

describe('KafkaAdmin queries', () => {
    beforeEach(() => {
        vi.mocked(getAvailableTopics).mockResolvedValue(availableTopics)
        vi.mocked(getConsumerGroups).mockImplementation(async ({topicName}) => [
            `${topicName}-group`
        ])
    })

    afterEach(() => {
        cleanup()
        queryClients.forEach((queryClient) => queryClient.clear())
        queryClients.length = 0
        vi.clearAllMocks()
    })

    it('loads groups only for supported selected topics and keeps results topic-scoped', async () => {
        renderKafkaAdmin()

        const topicSelector = await screen.findByLabelText('Select Kafka Topic')
        expect(getAvailableTopics).toHaveBeenCalledTimes(1)
        expect(getConsumerGroups).not.toHaveBeenCalled()

        fireEvent.change(topicSelector, {target: {value: 'on-prem-topic'}})
        expect(getConsumerGroups).not.toHaveBeenCalled()

        fireEvent.change(topicSelector, {target: {value: 'aiven-topic'}})
        expect(
            await screen.findAllByRole('option', {name: 'aiven-topic-group'})
        ).toHaveLength(2)

        fireEvent.change(topicSelector, {
            target: {value: 'another-aiven-topic'}
        })
        expect(
            await screen.findAllByRole('option', {
                name: 'another-aiven-topic-group'
            })
        ).toHaveLength(2)

        expect(getConsumerGroups).toHaveBeenCalledTimes(2)
        expect(getConsumerGroups).toHaveBeenNthCalledWith(1, {
            topicName: 'aiven-topic'
        })
        expect(getConsumerGroups).toHaveBeenNthCalledWith(2, {
            topicName: 'another-aiven-topic'
        })
    })

    it('shows a toast when loading consumer groups fails', async () => {
        vi.mocked(getConsumerGroups).mockRejectedValueOnce(
            new Error('Request failed')
        )

        renderKafkaAdmin()

        const topicSelector = await screen.findByLabelText('Select Kafka Topic')
        fireEvent.change(topicSelector, {target: {value: 'aiven-topic'}})

        await waitFor(() => {
            expect(errorToast).toHaveBeenCalledWith(
                'Unable to load consumer groups'
            )
        })
    })

    it('does not repeat a cached consumer-group error while a topic is refetching', async () => {
        let resolveGroupRefresh: ((groups: string[]) => void) | undefined
        vi.mocked(getConsumerGroups)
            .mockRejectedValueOnce(new Error('Initial request failed'))
            .mockResolvedValueOnce(['another-aiven-topic-group'])
            .mockImplementationOnce(
                () =>
                    new Promise(resolve => {
                        resolveGroupRefresh = resolve
                    })
            )

        renderKafkaAdmin()

        const topicSelector = await screen.findByLabelText('Select Kafka Topic')
        fireEvent.change(topicSelector, {target: {value: 'aiven-topic'}})
        await waitFor(() => expect(errorToast).toHaveBeenCalledTimes(1))

        fireEvent.change(topicSelector, {target: {value: 'another-aiven-topic'}})
        await screen.findAllByRole('option', {name: 'another-aiven-topic-group'})

        fireEvent.change(topicSelector, {target: {value: 'aiven-topic'}})
        await waitFor(() => expect(getConsumerGroups).toHaveBeenCalledTimes(3))
        expect(errorToast).toHaveBeenCalledTimes(1)

        if (resolveGroupRefresh == null) {
            throw new Error('The cached consumer-group refresh did not start')
        }
        resolveGroupRefresh(['aiven-topic-group'])

        await screen.findAllByRole('option', {name: 'aiven-topic-group'})
        expect(errorToast).toHaveBeenCalledTimes(1)
    })

    it('shows a toast when loading available topics fails', async () => {
        vi.mocked(getAvailableTopics).mockRejectedValueOnce(
            new Error('Request failed')
        )

        renderKafkaAdmin()

        await waitFor(() => {
            expect(errorToast).toHaveBeenCalledWith(
                'Unable to load available topics'
            )
        })
        expect(screen.getByLabelText('Select Kafka Topic')).toBeTruthy()
    })

    it('fetches last record offsets on demand and clears them when the topic changes', async () => {
        vi.mocked(getLastRecordOffsets).mockImplementation(async ({topicName}) => [
            {topicName, topicPartition: 0, offset: topicName === 'aiven-topic' ? 7 : 9}
        ])

        renderKafkaAdmin()

        const topicSelector = await screen.findByLabelText('Select Kafka Topic')
        expect(getLastRecordOffsets).not.toHaveBeenCalled()

        fireEvent.change(topicSelector, {target: {value: 'aiven-topic'}})
        const [lastRecordOffsetsFetch] = screen.getAllByRole('button', {name: 'Fetch'})
        fireEvent.click(lastRecordOffsetsFetch)

        expect(await screen.findByText('7')).toBeTruthy()
        expect(getLastRecordOffsets).toHaveBeenCalledTimes(1)
        expect(getLastRecordOffsets).toHaveBeenNthCalledWith(1, {topicName: 'aiven-topic'})

        fireEvent.change(topicSelector, {target: {value: 'another-aiven-topic'}})
        expect(screen.queryByText('7')).toBeNull()
        expect(getLastRecordOffsets).toHaveBeenCalledTimes(1)

        fireEvent.click(screen.getAllByRole('button', {name: 'Fetch'})[0])
        expect(await screen.findByText('9')).toBeTruthy()
        expect(getLastRecordOffsets).toHaveBeenCalledTimes(2)
        expect(getLastRecordOffsets).toHaveBeenNthCalledWith(2, {topicName: 'another-aiven-topic'})
    })

    it('shows a toast when fetching last record offsets fails', async () => {
        vi.mocked(getLastRecordOffsets).mockRejectedValueOnce(new Error('Request failed'))

        renderKafkaAdmin()

        const topicSelector = await screen.findByLabelText('Select Kafka Topic')
        fireEvent.change(topicSelector, {target: {value: 'aiven-topic'}})
        fireEvent.click(screen.getAllByRole('button', {name: 'Fetch'})[0])

        await waitFor(() => {
            expect(errorToast).toHaveBeenCalledWith('Klarte ikke å hente siste record offset')
        })
    })

    it('loads consumer and log-end offsets for the selected topic and group on demand', async () => {
        vi.mocked(getConsumerOffsets)
            .mockResolvedValueOnce([{topicName: 'aiven-topic', topicPartition: 0, offset: 5}])
            .mockResolvedValueOnce([{topicName: 'aiven-topic', topicPartition: 0, offset: 6}])
        vi.mocked(getLastRecordOffsets)
            .mockResolvedValueOnce([{topicName: 'aiven-topic', topicPartition: 0, offset: 8}])
            .mockResolvedValueOnce([{topicName: 'aiven-topic', topicPartition: 0, offset: 9}])

        renderKafkaAdmin()
        await selectConsumerGroup('aiven-topic', 'aiven-topic-group')
        expect(getConsumerOffsets).not.toHaveBeenCalled()
        expect(getLastRecordOffsets).not.toHaveBeenCalled()

        fireEvent.click(screen.getAllByRole('button', {name: 'Fetch'})[1])
        expect(await screen.findByText('5')).toBeTruthy()
        expect(getConsumerOffsets).toHaveBeenCalledWith({
            topicName: 'aiven-topic',
            groupId: 'aiven-topic-group'
        })
        expect(getLastRecordOffsets).toHaveBeenCalledWith({topicName: 'aiven-topic'})
        expect(screen.getByTitle('Consumer offset is behind the log end')).toBeTruthy()

        fireEvent.click(screen.getAllByRole('button', {name: 'Fetch'})[1])
        expect(await screen.findByText('6')).toBeTruthy()
        expect(getConsumerOffsets).toHaveBeenCalledTimes(2)
        expect(getLastRecordOffsets).toHaveBeenCalledTimes(2)
    })

    it('shows a warning when the consumer group has no offsets', async () => {
        vi.mocked(getConsumerOffsets).mockResolvedValue([])

        renderKafkaAdmin()
        await selectConsumerGroup('aiven-topic', 'aiven-topic-group')
        fireEvent.click(screen.getAllByRole('button', {name: 'Fetch'})[1])

        await waitFor(() => {
            expect(warningToast).toHaveBeenCalledWith(
                'Fant ingen offsets tilhørende konsumeren for topicen'
            )
        })
    })

    it('shows a toast when fetching consumer offsets fails', async () => {
        vi.mocked(getConsumerOffsets).mockRejectedValueOnce(new Error('Request failed'))

        renderKafkaAdmin()
        await selectConsumerGroup('aiven-topic', 'aiven-topic-group')
        fireEvent.click(screen.getAllByRole('button', {name: 'Fetch'})[1])

        await waitFor(() => {
            expect(errorToast).toHaveBeenCalledWith('Klarte ikke å hente consumer offsets')
        })
    })
})
