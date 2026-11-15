// @vitest-environment jsdom

import {cleanup, fireEvent, render, screen} from '@testing-library/react'
import {afterEach, describe, expect, it, vi} from 'vitest'
import {getLastRecordOffset, readFromTopic} from '../../api'
import {ReadFromTopicCard} from './ReadFromTopicCard'

vi.mock('../../api', () => ({
    getLastRecordOffset: vi.fn(),
    readFromTopic: vi.fn()
}))

vi.mock('../../utils/toast-utils', () => ({
    errorToast: vi.fn(),
    warningToast: vi.fn()
}))

describe('ReadFromTopicCard layout', () => {
    afterEach(() => {
        cleanup()
        vi.clearAllMocks()
    })

    it('renders read controls and disables Fetch without a selected topic', () => {
        render(<ReadFromTopicCard selectedTopic={null}/>)

        expect(screen.getByText('Read records from topic')).toBeTruthy()
        expect(screen.getByLabelText('Max records (maximum of records that will be returned, max=100)')).toBeTruthy()
        expect((screen.getByRole('button', {name: 'Fetch'}) as HTMLButtonElement).disabled).toBe(true)
    })
})

describe('ReadFromTopicCard interactions', () => {
    afterEach(() => {
        cleanup()
        vi.clearAllMocks()
    })

    it('reads and displays records from the selected topic', async () => {
        vi.mocked(getLastRecordOffset).mockResolvedValue({latestRecordOffset: 50})
        vi.mocked(readFromTopic).mockResolvedValue([
            {
                partition: 0,
                key: 'record-key',
                value: 'record-value',
                timestamp: 0,
                offset: 0,
                headers: []
            }
        ])
        render(<ReadFromTopicCard selectedTopic="aiven-topic"/>)

        fireEvent.click(screen.getByRole('button', {name: 'Fetch'}))

        expect(await screen.findByText('record-value')).toBeTruthy()
        expect(getLastRecordOffset).toHaveBeenCalledWith({
            topicName: 'aiven-topic',
            topicPartition: 0
        })
        expect(readFromTopic).toHaveBeenCalledWith({
            topicName: 'aiven-topic',
            topicAllPartitions: true,
            topicPartition: 0,
            fromOffset: 0,
            maxRecords: 50,
            filterText: ''
        })
    })
})
