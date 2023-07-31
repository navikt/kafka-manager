import React from 'react';
import './kafka-record-modal-content.css';
import { isJson, NO_OP } from '../../utils';
import { BodyShort, Label, Textarea, TextField } from '@navikt/ds-react';
import { toTimestamp } from '../../utils/date-utils';
import ReactJson from 'react-json-view';
import { KafkaRecord } from '../../api';

export function KafkaRecordModalContent(props: { record: KafkaRecord | null }) {
	if (props.record == null) {
		return null;
	}

	const { key, value, offset, timestamp, headers } = props.record;
	const safeValue = value || '';
	const isRecordValueJson = isJson(safeValue);

	return (
		<div className="kafka-record-modal-content">
			<div className="blokk-m">
				<TextField label="Offset" value={offset} readOnly={true} />
				<TextField label="Key" value={key || 'NO_KEY'} readOnly={true} />
				<TextField label="Timestamp" value={toTimestamp(timestamp)} readOnly={true} />

				<Label htmlFor="label">Headers</Label>
				{headers.length > 0 ? (
					<ul>
						{headers.map((header, idx) => {
							return (
								<li key={idx}>
									Name={header.name} Value={header.value}
								</li>
							);
						})}
					</ul>
				) : (
					<BodyShort>No headers</BodyShort>
				)}
			</div>

			{isRecordValueJson ? (
				<ReactJson name={false} src={JSON.parse(safeValue)} />
			) : (
				<Textarea label="Payload" value={safeValue} readOnly={true} onChange={NO_OP} />
			)}
		</div>
	);
}
