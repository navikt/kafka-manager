import {cleanup, fireEvent, render, screen, waitFor} from '@testing-library/react'
import {afterEach, describe, expect, it, vi} from 'vitest'
import {deleteConsumerRecords} from '../../api'
import {errorToast, successToast} from '../../utils/toast-utils'
import {DeleteRecordsCard} from './DeleteRecordsCard'

vi.mock('../../api', () => ({
    deleteConsumerRecords: vi.fn()
}))

vi.mock('../../utils/toast-utils', () => ({
    errorToast: vi.fn(),
    successToast: vi.fn()
}))

describe('DeleteRecordsCard layout', () => {
    afterEach(() => {
        cleanup()
        vi.clearAllMocks()
    })

    it('disables record deletion for unsupported topics', () => {
        render(<DeleteRecordsCard selectedTopic="on-prem-topic" supportsRecordDeletion={false}/>)

        expect(screen.getByText('Record deletion is only supported for Aiven topics.')).toBeTruthy()
        expect((screen.getByRole('button', {name: 'Delete records'}) as HTMLButtonElement).disabled).toBe(true)
    })

    it('shows a destructive warning and enables deletion for supported topics', () => {
        render(<DeleteRecordsCard selectedTopic="aiven-topic" supportsRecordDeletion={true}/>)

        expect(screen.getByText('Danger zone')).toBeTruthy()
        expect(screen.getByText(/This operation will permanently delete all records/)).toBeTruthy()
        expect((screen.getByRole('button', {name: 'Delete records'}) as HTMLButtonElement).disabled).toBe(false)
    })
})

describe('DeleteRecordsCard interactions', () => {
    afterEach(() => {
        cleanup()
        vi.clearAllMocks()
    })

    it('deletes records only after confirmation', async () => {
        vi.mocked(deleteConsumerRecords).mockResolvedValue(undefined)
        render(<DeleteRecordsCard selectedTopic="aiven-topic" supportsRecordDeletion={true}/>)

        fireEvent.change(screen.getByLabelText('Topic partition (first partition starts at 0)'), {
            target: {value: '3'}
        })
        fireEvent.change(screen.getByLabelText('Delete all records before offset'), {
            target: {value: '42'}
        })
        fireEvent.click(screen.getByRole('button', {name: 'Delete records'}))

        expect(deleteConsumerRecords).not.toHaveBeenCalled()
        expect(screen.getByText('Confirm delete records')).toBeTruthy()
        expect(screen.getByText('3', {selector: 'strong'})).toBeTruthy()
        expect(screen.getByText('42', {selector: 'strong'})).toBeTruthy()

        fireEvent.click(screen.getByRole('button', {name: 'Yes, delete'}))

        await waitFor(() => {
            expect(deleteConsumerRecords).toHaveBeenCalledWith({
                topicName: 'aiven-topic',
                topicPartition: 3,
                beforeOffset: 42
            })
            expect(successToast).toHaveBeenCalledWith('Records deleted successfully')
        })
    })

    it('does not delete records when confirmation is cancelled', () => {
        render(<DeleteRecordsCard selectedTopic="aiven-topic" supportsRecordDeletion={true}/>)

        fireEvent.click(screen.getByRole('button', {name: 'Delete records'}))
        fireEvent.click(screen.getByRole('button', {name: 'Cancel'}))

        expect(deleteConsumerRecords).not.toHaveBeenCalled()
    })

    it('shows an error and does not delete when the input is invalid', async () => {
        render(<DeleteRecordsCard selectedTopic="aiven-topic" supportsRecordDeletion={true}/>)

        fireEvent.change(screen.getByLabelText('Topic partition (first partition starts at 0)'), {
            target: {value: '-1'}
        })
        fireEvent.click(screen.getByRole('button', {name: 'Delete records'}))
        fireEvent.click(screen.getByRole('button', {name: 'Yes, delete'}))

        await waitFor(() => {
            expect(errorToast).toHaveBeenCalledWith('Partition and offset must be non-negative integers')
        })
        expect(deleteConsumerRecords).not.toHaveBeenCalled()
    })
})
