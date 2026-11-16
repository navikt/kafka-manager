import {render, screen} from '@testing-library/react'
import {describe, expect, it} from 'vitest'
import {KafkaRecord} from '../../api'
import {KafkaRecordModalContent} from './KafkaRecordModalContent'

const record: KafkaRecord = {
    partition: 2,
    key: 'record-key',
    value: 'plain payload',
    timestamp: 0,
    offset: 42,
    headers: [{name: 'source', value: 'test'}]
}

describe('KafkaRecordModalContent layout', () => {
    it('renders record metadata, headers, and a text payload', () => {
        render(<KafkaRecordModalContent record={record}/>)

        expect((screen.getByLabelText('Partition') as HTMLInputElement).value).toBe('2')
        expect((screen.getByLabelText('Offset') as HTMLInputElement).value).toBe('42')
        expect((screen.getByLabelText('Key') as HTMLInputElement).value).toBe('record-key')
        expect(screen.getByText('Name=source Value=test')).toBeTruthy()
        expect((screen.getByLabelText('Payload') as HTMLTextAreaElement).value).toBe('plain payload')
    })

    it('renders nothing when no record is selected', () => {
        const {container} = render(<KafkaRecordModalContent record={null}/>)

        expect(container.firstChild).toBeNull()
    })
})
