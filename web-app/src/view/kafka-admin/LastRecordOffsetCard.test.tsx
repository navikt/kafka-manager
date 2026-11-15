// @vitest-environment jsdom

import {QueryClient, QueryClientProvider} from '@tanstack/react-query'
import {cleanup, fireEvent, render, screen, waitFor} from '@testing-library/react'
import {afterEach, describe, expect, it, vi} from 'vitest'
import {getLastRecordOffsets} from '../../api'
import {errorToast} from '../../utils/toast-utils'
import {LastRecordOffsetCard} from './LastRecordOffsetCard'

vi.mock('../../api', () => ({
    getLastRecordOffsets: vi.fn()
}))

vi.mock('../../utils/toast-utils', () => ({
    errorToast: vi.fn()
}))

const renderCard = (selectedTopic: string | null) => {
    const queryClient = new QueryClient({
        defaultOptions: {queries: {retry: false}}
    })
    const view = render(
        <QueryClientProvider client={queryClient}>
            <LastRecordOffsetCard selectedTopic={selectedTopic}/>
        </QueryClientProvider>
    )
    return {queryClient, ...view}
}

describe('LastRecordOffsetCard layout', () => {
    afterEach(() => {
        cleanup()
        vi.clearAllMocks()
    })

    it('disables fetching when no topic is selected', () => {
        const {queryClient} = renderCard(null)

        expect(screen.getByText('Last record offsets')).toBeTruthy()
        expect((screen.getByRole('button', {name: 'Fetch'}) as HTMLButtonElement).disabled).toBe(true)
        queryClient.clear()
    })
})

describe('LastRecordOffsetCard interactions', () => {
    afterEach(() => {
        cleanup()
        vi.clearAllMocks()
    })

    it('fetches offsets for the selected topic when requested', async () => {
        vi.mocked(getLastRecordOffsets).mockResolvedValue([
            {topicName: 'aiven-topic', topicPartition: 0, offset: 8}
        ])
        const {queryClient} = renderCard('aiven-topic')

        fireEvent.click(screen.getByRole('button', {name: 'Fetch'}))

        expect(await screen.findByText('8')).toBeTruthy()
        expect(getLastRecordOffsets).toHaveBeenCalledWith({topicName: 'aiven-topic'})
        queryClient.clear()
    })

    it('does not re-toast a cached failure when returning to a topic', async () => {
        vi.mocked(getLastRecordOffsets)
            .mockRejectedValueOnce(new Error('First request failed'))
            .mockRejectedValueOnce(new Error('Refresh failed'))
        const {queryClient, rerender} = renderCard('aiven-topic')

        fireEvent.click(screen.getByRole('button', {name: 'Fetch'}))
        await waitFor(() => expect(errorToast).toHaveBeenCalledTimes(1))

        rerender(
            <QueryClientProvider client={queryClient}>
                <LastRecordOffsetCard selectedTopic="another-topic"/>
            </QueryClientProvider>
        )
        rerender(
            <QueryClientProvider client={queryClient}>
                <LastRecordOffsetCard selectedTopic="aiven-topic"/>
            </QueryClientProvider>
        )

        expect(getLastRecordOffsets).toHaveBeenCalledTimes(1)
        expect(errorToast).toHaveBeenCalledTimes(1)

        fireEvent.click(screen.getByRole('button', {name: 'Fetch'}))
        await waitFor(() => {
            expect(getLastRecordOffsets).toHaveBeenCalledTimes(2)
            expect(errorToast).toHaveBeenCalledTimes(2)
        })
        queryClient.clear()
    })
})
