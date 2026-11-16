import {cleanup, fireEvent, render, screen} from '@testing-library/react'
import {afterEach, describe, expect, it} from 'vitest'
import {KafkaRecord} from '../../api'
import {ReadFromTopicResults} from './ReadFromTopicResults'

const record: KafkaRecord = {
    partition: 0,
    key: 'record-key',
    value: 'record-value',
    timestamp: 0,
    offset: 42,
    headers: []
}

describe('ReadFromTopicResults layout', () => {
    afterEach(cleanup)

    it('shows the loading indicator while records are being fetched', () => {
        render(
            <ReadFromTopicResults
                selectedTopic="aiven-topic"
                isLoading={true}
                records={[]}
                timeTakenMs={1000}
            />
        )

        expect(screen.getByText('1.0s')).toBeTruthy()
        expect(screen.queryByRole('table')).toBeNull()
    })

    it('renders record rows when records are available', () => {
        render(
            <ReadFromTopicResults
                selectedTopic="aiven-topic"
                isLoading={false}
                records={[record]}
                timeTakenMs={0}
            />
        )

        expect(screen.getByText('record-value')).toBeTruthy()
        expect(screen.getByText('42')).toBeTruthy()
    })
})

describe('ReadFromTopicResults interactions', () => {
    afterEach(cleanup)

    it('opens the record details when View is clicked', async () => {
        render(
            <ReadFromTopicResults
                selectedTopic="aiven-topic"
                isLoading={false}
                records={[record]}
                timeTakenMs={0}
            />
        )

        fireEvent.click(screen.getByRole('button', {name: 'View record at partition 0, offset 42'}))

        expect(await screen.findByLabelText('Payload')).toBeTruthy()
        expect((screen.getByLabelText('Payload') as HTMLTextAreaElement).value).toBe('record-value')
    })
})
