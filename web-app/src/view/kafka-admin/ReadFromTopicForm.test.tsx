// @vitest-environment jsdom

import {cleanup, fireEvent, render, screen} from '@testing-library/react'
import {afterEach, describe, expect, it, vi} from 'vitest'
import {ReadFromTopicForm, ReadFromTopicFormValues} from './ReadFromTopicForm'

describe('ReadFromTopicForm layout', () => {
    afterEach(cleanup)

    it('renders the read controls and disables Fetch without a selected topic', () => {
        render(<ReadFromTopicForm selectedTopic={null} onSubmit={vi.fn()}/>)

        expect(screen.getByText('Topic partitions')).toBeTruthy()
        expect(screen.getByText('Fetch from')).toBeTruthy()
        expect(screen.getByLabelText('Max records (maximum of records that will be returned, max=100)')).toBeTruthy()
        expect((screen.getByRole('button', {name: 'Fetch'}) as HTMLButtonElement).disabled).toBe(true)
    })
})

describe('ReadFromTopicForm interactions', () => {
    afterEach(cleanup)

    it('submits the current form values', () => {
        const onSubmit = vi.fn<(values: ReadFromTopicFormValues) => void>()
        render(<ReadFromTopicForm selectedTopic="aiven-topic" onSubmit={onSubmit}/>)

        fireEvent.change(screen.getByLabelText('Max records (maximum of records that will be returned, max=100)'), {
            target: {value: '25'}
        })
        fireEvent.change(screen.getByLabelText('Key/value filter (tip: max=1 can be used to reduce waiting)'), {
            target: {value: 'test-filter'}
        })
        fireEvent.click(screen.getByRole('button', {name: 'Fetch'}))

        expect(onSubmit).toHaveBeenCalledWith({
            partitionMode: 'all',
            topicPartitionField: '0',
            fetchFromField: 'END',
            fromOffsetField: '0',
            maxRecordsField: '25',
            keyValueFilterField: 'test-filter'
        })
    })
})
