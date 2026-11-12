import { Button, LocalAlert, TextField, Modal } from '@navikt/ds-react'
import { useState } from 'react'
import { deleteConsumerRecords } from '../../api'
import { errorToast, successToast } from '../../utils/toast-utils'
import { SelectedTopic } from './SelectedTopic'
import { Card } from '../../component/card/card'
import { parseNonNegativeSafeInteger } from '../../utils'

export function DeleteRecordsCard(props: { selectedTopic: string | null; supportsRecordDeletion: boolean }) {
	const [partitionField, setPartitionField] = useState('0')
	const [offsetField, setOffsetField] = useState('0')
	const [isLoading, setIsLoading] = useState(false)
	const [showConfirm, setShowConfirm] = useState(false)

	function handleDeleteRecords() {
		if (props.selectedTopic == null) {
			errorToast('Please select a topic from the sidebar')
			return
		}
		if (!props.supportsRecordDeletion) {
			errorToast('Record deletion is only supported for Aiven topics')
			return
		}
		const topicPartition = parseNonNegativeSafeInteger(partitionField)
		const beforeOffset = parseNonNegativeSafeInteger(offsetField)
		if (topicPartition == null || beforeOffset == null) {
			errorToast('Partition and offset must be non-negative integers')
			return
		}
		setIsLoading(true)
		deleteConsumerRecords({
			topicName: props.selectedTopic,
			topicPartition,
			beforeOffset
		})
			.then(() => successToast('Records deleted successfully'))
			.catch(() => errorToast('Failed to delete records'))
			.finally(() => setIsLoading(false))
	}

	function handleDeleteClick() {
		setShowConfirm(true)
	}

	function handleConfirm() {
		setShowConfirm(false)
		handleDeleteRecords()
	}

	function handleCancel() {
		setShowConfirm(false)
	}

	return (
		<Card title="Delete records from topic" innholdClassName="delete-records-card card__content">
			<LocalAlert status={'warning'}>
				<LocalAlert.Header>
					<LocalAlert.Title>
						{props.supportsRecordDeletion ? 'Danger zone' : 'Record deletion unavailable'}
					</LocalAlert.Title>
				</LocalAlert.Header>
				<LocalAlert.Content>
					{props.supportsRecordDeletion ? (
						<>
							This operation will permanently delete all records in the selected topic{' '}
							<strong>{props.selectedTopic ?? '(none selected)'}</strong> partition before the specified
							offset. This cannot be undone!
						</>
					) : props.selectedTopic ? (
						'Record deletion is only supported for Aiven topics.'
					) : (
						'Select an Aiven topic to enable record deletion.'
					)}
				</LocalAlert.Content>
			</LocalAlert>
			<SelectedTopic selectedTopic={props.selectedTopic} />
			<TextField
				label="Topic partition (first partition starts at 0)"
				type="number"
				value={partitionField}
				onChange={e => setPartitionField(e.target.value)}
				disabled={!props.selectedTopic || !props.supportsRecordDeletion}
			/>
			<TextField
				label="Delete all records before offset"
				type="number"
				value={offsetField}
				onChange={e => setOffsetField(e.target.value)}
				disabled={!props.selectedTopic || !props.supportsRecordDeletion}
			/>
			<Button
				onClick={handleDeleteClick}
				variant="danger"
				loading={isLoading}
				disabled={!props.selectedTopic || !props.supportsRecordDeletion || isLoading}
				style={{ marginTop: '1rem' }}
			>
				Delete records
			</Button>
			<Modal
				open={showConfirm}
				onClose={handleCancel}
				header={{ heading: 'Confirm delete records', closeButton: true }}
			>
				<Modal.Body>
					Are you absolutely sure you want to delete all records in <strong>{props.selectedTopic}</strong>{' '}
					partition <strong>{partitionField}</strong> before offset <strong>{offsetField}</strong>? This
					action cannot be undone.
				</Modal.Body>
				<Modal.Footer>
					<Button variant="danger" onClick={handleConfirm} loading={isLoading} disabled={isLoading}>
						Yes, delete
					</Button>
					<Button variant="secondary" onClick={handleCancel} disabled={isLoading}>
						Cancel
					</Button>
				</Modal.Footer>
			</Modal>
		</Card>
	)
}
