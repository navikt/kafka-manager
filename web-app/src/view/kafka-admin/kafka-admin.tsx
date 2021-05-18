import React, { useState } from 'react';
import { errorToast, successToast, warningToast } from '../../utils/toast-utils';
import { Card } from '../../component/card/card';
import { Flatknapp } from 'nav-frontend-knapper';
import {
	getConsumerOffsets,
	GetConsumerOffsetsRequest,
	getLastRecordOffset,
	GetLastRecordOffsetRequest,
	KafkaRecord,
	readFromTopic,
	ReadFromTopicRequest,
	setConsumerOffset,
	SetConsumerOffsetRequest,
	TopicPartitionOffset
} from '../../api';
import { Input, Select } from 'nav-frontend-skjema';
import { Normaltekst } from 'nav-frontend-typografi';
import constate from 'constate';
import Modal from 'nav-frontend-modal';
import './kafka-admin.less';
import { KafkaRecordModalContent } from './kafka-record-modal-content';

const [CredentialsStoreProvider, useCredentialsStore] = constate(() => {
	const [username, setUsername] = useState('');
	const [password, setPassword] = useState('');
	return { username, setUsername, password, setPassword };
});

export function KafkaAdmin() {
	return (
		<div className="view kafka-admin">
			<CredentialsStoreProvider>
				<div>
					<div className="card-row-4-col blokk-m">
						<CredentialsCard />
						<ConsumerOffsetsCard />
						<LastRecordOffsetCard />
						<SetConsumerOffsetCard />
					</div>
					<ReadFromTopicCard />
				</div>
			</CredentialsStoreProvider>
		</div>
	);
}

function CredentialsCard() {
	const { username, setUsername, password, setPassword } = useCredentialsStore();

	return (
		<Card title="Credentials" innholdClassName="card__content">
			<Normaltekst className="blokk-s">
				Alle funksjoner på denne siden krever at credentials fra en systembruker er utfylt
			</Normaltekst>

			<Input label="Username" value={username} onChange={e => setUsername(e.target.value)} />
			{/* Set "one-time-code" for å prøve å forhindre at browseren lagrer passordet */}
			<Input
				label="Password"
				type="password"
				autoComplete="one-time-code"
				value={password}
				onChange={e => setPassword(e.target.value)}
			/>
		</Card>
	);
}

function ConsumerOffsetsCard() {
	const { username, password } = useCredentialsStore();
	const [groupId, setGroupId] = useState('');
	const [topicName, setTopicName] = useState('');

	const [topicPartitionOffsets, setTopicPartitionOffsets] = useState<TopicPartitionOffset[]>([]);

	function handleHentConsumerOffsets() {
		const request: GetConsumerOffsetsRequest = {
			username,
			password,
			groupId,
			topicName
		};

		getConsumerOffsets(request)
			.then(res => {
				if (res.data.length === 0) {
					warningToast('Fant ingen offsets tilhørende konsumeren for topicen');
				} else {
					setTopicPartitionOffsets(res.data);
				}
			})
			.catch(() => errorToast('Klarte ikke å hente consumer offsets'));
	}

	return (
		<Card title="Get consumer offsets" innholdClassName="card__content">
			<Normaltekst className="blokk-s">
				Henter siste commitet offset for alle partisjoner tilhørende en consumer gruppe for en gitt topic
			</Normaltekst>

			<Input label="Consumer group id" value={groupId} onChange={e => setGroupId(e.target.value)} />
			<Input label="Topic name" value={topicName} onChange={e => setTopicName(e.target.value)} />

			<Flatknapp onClick={handleHentConsumerOffsets}>Fetch</Flatknapp>

			<ul>
				{topicPartitionOffsets.map((tpo, idx) => {
					return (
						<li key={idx}>
							Partition={tpo.topicPartition} Offset={tpo.offset}
						</li>
					);
				})}
			</ul>
		</Card>
	);
}

function LastRecordOffsetCard() {
	const { username, password } = useCredentialsStore();
	const [topicName, setTopicName] = useState('');
	const [topicPartition, setTopicPartition] = useState('0');

	const [lastRecordOffset, setLastRecordOffset] = useState<number | null>(null);

	function handleHentLastRecordOffset() {
		const request: GetLastRecordOffsetRequest = {
			username,
			password,
			topicName,
			topicPartition: parseInt(topicPartition, 10)
		};

		getLastRecordOffset(request)
			.then(res => {
				setLastRecordOffset(res.data.latestRecordOffset);
			})
			.catch(() => errorToast('Klarte ikke å hente siste record offset'));
	}

	return (
		<Card title="Last record offset" innholdClassName="card__content">
			<Normaltekst className="blokk-s">
				Henter offset til siste record(melding på kafka) som ligger på en topic+partisjon
			</Normaltekst>

			<Input label="Topic name" value={topicName} onChange={e => setTopicName(e.target.value)} />
			<Input
				label="Topic partition (first partition starts at 0)"
				type="number"
				value={topicPartition}
				onChange={e => setTopicPartition(e.target.value)}
			/>

			<Flatknapp onClick={handleHentLastRecordOffset}>Fetch</Flatknapp>

			{lastRecordOffset != null ? (
				<Normaltekst style={{ marginTop: '2rem' }}>
					Offset til siste record: <strong>{lastRecordOffset}</strong>
				</Normaltekst>
			) : null}
		</Card>
	);
}

function SetConsumerOffsetCard() {
	const { username, password } = useCredentialsStore();
	const [topicNameField, setTopicNameField] = useState('');
	const [groupIdField, setGroupIdField] = useState('');
	const [topicPartitionField, setTopicPartitionField] = useState('0');
	const [offsetField, setOffsetField] = useState('0');

	function handleSetConsumerOffset() {
		const request: SetConsumerOffsetRequest = {
			username,
			password,
			topicName: topicNameField,
			topicPartition: parseInt(topicPartitionField, 10),
			offset: parseInt(offsetField, 10),
			groupId: groupIdField
		};

		setConsumerOffset(request)
			.then(() => successToast('Consumer offset har blitt endret'))
			.catch(() => errorToast('Klarte ikke å sette consumer offset'));
	}

	return (
		<Card title="Set consumer offset" className="mini-card" innholdClassName="card__content">
			<Normaltekst className="blokk-s">
				Setter offset til en consumer for en topic+partisjon. Det er viktig å vite at selv om offsetet blir
				endret, så vil ikke consumere plukke opp endringen i offset før de er startet på nytt. Hvis en consumer
				committer et nytt offset før den har blitt startet på nytt og fått hentet inn endringen, så vil den
				overskrive offsetet fra pto-admin.
			</Normaltekst>

			<Input label="Topic name" value={topicNameField} onChange={e => setTopicNameField(e.target.value)} />
			<Input
				label="Topic partition (first partition starts at 0)"
				type="number"
				value={topicPartitionField}
				onChange={e => setTopicPartitionField(e.target.value)}
			/>
			<Input label="Consumer group id" value={groupIdField} onChange={e => setGroupIdField(e.target.value)} />
			<Input label="Offset" type="number" value={offsetField} onChange={e => setOffsetField(e.target.value)} />

			<Flatknapp onClick={handleSetConsumerOffset}>Set offset</Flatknapp>
		</Card>
	);
}

enum FetchFrom {
	BEGINNING = 'BEGINNING',
	END = 'END',
	OFFSET = 'OFFSET'
}

function ReadFromTopicCard() {
	const { username, password } = useCredentialsStore();
	const [topicNameField, setTopicNameField] = useState('');
	const [topicPartitionField, setTopicPartitionField] = useState('0');
	const [fetchFromField, setFetchFromField] = useState<FetchFrom>(FetchFrom.END);
	const [fromOffsetField, setFromOffsetField] = useState('0');
	const [maxRecordsField, setMaxRecordsField] = useState('50');

	const [clickedRecord, setClickedRecord] = useState<KafkaRecord | null>(null);
	const [recordsFromTopic, setRecordsFromTopic] = useState<KafkaRecord[]>([]);

	async function handleReadFromTopic() {
		const topicPartition = parseInt(topicPartitionField, 10);
		const maxRecords = parseInt(maxRecordsField, 10);

		let fetchFromOffset;

		if (fetchFromField === FetchFrom.BEGINNING) {
			fetchFromOffset = 0;
		} else if (fetchFromField === FetchFrom.END) {
			try {
				const lastRecordOffset = (
					await getLastRecordOffset({
						username,
						password,
						topicName: topicNameField,
						topicPartition
					})
				).data.latestRecordOffset;

				fetchFromOffset = lastRecordOffset - maxRecords;
			} catch (e) {
				errorToast('Klarte ikke å hente siste record offset');
				return;
			}
		} else {
			fetchFromOffset = parseInt(fromOffsetField, 10);
		}

		const request: ReadFromTopicRequest = {
			username,
			password,
			topicName: topicNameField,
			topicPartition,
			fromOffset: fetchFromOffset,
			maxRecords
		};

		readFromTopic(request)
			.then(res => {
				if (res.data.length === 0) {
					warningToast('Could not find any records in the topic+partition for the given offset');
				} else {
					setRecordsFromTopic(res.data);
				}
			})
			.catch(() => errorToast('Klarte ikke å hente siste record offset'));
	}

	return (
		<Card
			title="Read records from topic"
			className="very-large-card center-horizontal"
			innholdClassName="card__content"
		>
			<Normaltekst className="blokk-s">
				Leser meldinger fra en topic+partisjon. Trykk på en av meldingene for å se mer detaljert informasjon
			</Normaltekst>

			<Input label="Topic name" value={topicNameField} onChange={e => setTopicNameField(e.target.value)} />
			<Input
				label="Topic partition (first partition starts at 0)"
				type="number"
				value={topicPartitionField}
				onChange={e => setTopicPartitionField(e.target.value)}
			/>

			<Select
				label="Fetch records from"
				value={fetchFromField}
				onChange={e => setFetchFromField(e.target.value as FetchFrom)}
			>
				<option value={FetchFrom.BEGINNING}>Beginning (fetch the first records in the topic)</option>
				<option value={FetchFrom.END}>End (fetch the last records in the topic)</option>
				<option value={FetchFrom.OFFSET}>Offset (fetch from a specified offset)</option>
			</Select>

			{fetchFromField === FetchFrom.OFFSET ? (
				<Input
					label="From offset"
					type="number"
					value={fromOffsetField}
					onChange={e => setFromOffsetField(e.target.value)}
				/>
			) : null}

			<Input
				label="Max records (max=100)"
				type="number"
				value={maxRecordsField}
				onChange={e => setMaxRecordsField(e.target.value)}
			/>

			<Flatknapp onClick={handleReadFromTopic}>Fetch</Flatknapp>

			{recordsFromTopic.length > 0 ? (
				<table className="tabell tabell--stripet">
					<thead>
						<tr>
							<th>Offset</th>
							<th>Key</th>
							<th>Value</th>
						</tr>
					</thead>
					<tbody>
						{recordsFromTopic
							.sort((r1, r2) => r1.offset - r2.offset)
							.map(record => {
								return (
									<tr
										key={record.offset}
										onClick={() => setClickedRecord(record)}
										className="kafka-record-row"
									>
										<td>{record.offset}</td>
										<td>{record.key || 'NO_KEY'}</td>
										<td className="kafka-record-value">{record.value}</td>
									</tr>
								);
							})}
					</tbody>
				</table>
			) : null}
			<Modal
				isOpen={clickedRecord != null}
				onRequestClose={() => setClickedRecord(null)}
				closeButton={true}
				contentLabel="View kafka record"
			>
				<KafkaRecordModalContent record={clickedRecord} />
			</Modal>
		</Card>
	);
}
