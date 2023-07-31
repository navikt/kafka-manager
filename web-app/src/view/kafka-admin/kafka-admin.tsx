import React, { ChangeEvent, useEffect, useRef, useState } from 'react';
import { errorToast, successToast, warningToast } from '../../utils/toast-utils';
import { Card } from '../../component/card/card';
import { BodyLong, BodyShort, Button, Loader, Modal, Select, TextField } from '@navikt/ds-react';
import {
	getAvailableTopics,
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
import './kafka-admin.less';
import { KafkaRecordModalContent } from './kafka-record-modal-content';
import { PageSpinner } from '../../component/page-spinner/page-spinner';
import { toTimerStr } from '../../utils/date-utils';

export function KafkaAdmin() {
	const [availableTopics, setAvailableTopics] = useState<string[] | null>(null);

	useEffect(() => {
		getAvailableTopics()
			.then(res => {
				setAvailableTopics(res.data);
			})
			.catch(() => {
				errorToast('Unable to load available topics');
				setAvailableTopics([]);
			});
	}, []);

	if (availableTopics == null) {
		return <PageSpinner />;
	}

	return (
		<div className="view kafka-admin">
			<div className="kafka-admin__col">
				<ConsumerOffsetsCard availableTopics={availableTopics} />
				<LastRecordOffsetCard availableTopics={availableTopics} />
				<SetConsumerOffsetCard availableTopics={availableTopics} />
			</div>
			<div>
				<ReadFromTopicCard availableTopics={availableTopics} />
			</div>
		</div>
	);
}

function TopicSelect(props: { availableTopics: string[]; onTopicChanged: (topic: string | null) => void }) {
	const NO_TOPIC = 'NO_TOPIC';
	const [selectedTopic, setSelectedTopic] = useState(NO_TOPIC);
	const sortedTopics = props.availableTopics.sort();

	function handleTopicChanged(e: ChangeEvent<HTMLSelectElement>) {
		const selectedTopic = e.target.value;
		setSelectedTopic(selectedTopic);

		const changedTopic = selectedTopic === NO_TOPIC ? null : selectedTopic;
		props.onTopicChanged(changedTopic);
	}

	return (
		<Select label="Topic name" value={selectedTopic} onChange={handleTopicChanged}>
			<option value={NO_TOPIC}>Choose a topic</option>
			{sortedTopics.map((topic, idx) => {
				return (
					<option key={idx} value={topic}>
						{topic}
					</option>
				);
			})}
		</Select>
	);
}

function ConsumerOffsetsCard(props: { availableTopics: string[] }) {
	const [groupIdField, setGroupIdField] = useState('');
	const [topicNameField, setTopicNameField] = useState<string | null>(null);

	const [topicPartitionOffsets, setTopicPartitionOffsets] = useState<TopicPartitionOffset[]>([]);

	function handleHentConsumerOffsets() {
		if (topicNameField == null) {
			errorToast('Topic is missing');
			return;
		}

		const request: GetConsumerOffsetsRequest = { groupId: groupIdField, topicName: topicNameField };

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
		<Card title="Get consumer offsets" className="consumer-offset-card" innholdClassName="card__content">
			<BodyShort className="blokk-s">
				Henter siste commitet offset for alle partisjoner tilhørende en consumer gruppe for en gitt topic
			</BodyShort>

			<TextField label="Consumer group id" value={groupIdField} onChange={e => setGroupIdField(e.target.value)} />
			<TopicSelect availableTopics={props.availableTopics} onTopicChanged={setTopicNameField} />

			<Button onClick={handleHentConsumerOffsets} variant="tertiary">
				Fetch
			</Button>

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

function LastRecordOffsetCard(props: { availableTopics: string[] }) {
	const [topicNameField, setTopicNameField] = useState<string | null>(null);
	const [topicPartition, setTopicPartition] = useState('0');

	const [lastRecordOffset, setLastRecordOffset] = useState<number | null>(null);

	function handleHentLastRecordOffset() {
		if (topicNameField == null) {
			errorToast('Topic is missing');
			return;
		}

		const request: GetLastRecordOffsetRequest = {
			topicName: topicNameField,
			topicPartition: parseInt(topicPartition, 10)
		};

		getLastRecordOffset(request)
			.then(res => {
				setLastRecordOffset(res.data.latestRecordOffset);
			})
			.catch(() => errorToast('Klarte ikke å hente siste record offset'));
	}

	return (
		<Card title="Last record offset" className="last-record-offset-card" innholdClassName="card__content">
			<BodyShort className="blokk-s">
				Henter offset til siste record(melding på kafka) som ligger på en topic+partisjon
			</BodyShort>

			<TopicSelect availableTopics={props.availableTopics} onTopicChanged={setTopicNameField} />
			<TextField
				label="Topic partition (first partition starts at 0)"
				type="number"
				value={topicPartition}
				onChange={e => setTopicPartition(e.target.value)}
			/>

			<Button onClick={handleHentLastRecordOffset} variant="tertiary">
				Fetch
			</Button>

			{lastRecordOffset != null ? (
				<BodyShort style={{ marginTop: '2rem' }}>
					Offset til siste record: <strong>{lastRecordOffset}</strong>
				</BodyShort>
			) : null}
		</Card>
	);
}

function SetConsumerOffsetCard(props: { availableTopics: string[] }) {
	const [topicNameField, setTopicNameField] = useState<string | null>(null);
	const [groupIdField, setGroupIdField] = useState('');
	const [topicPartitionField, setTopicPartitionField] = useState('0');
	const [offsetField, setOffsetField] = useState('0');

	function handleSetConsumerOffset() {
		if (topicNameField == null) {
			errorToast('Topic is missing');
			return;
		}

		const request: SetConsumerOffsetRequest = {
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
		<Card title="Set consumer offset" className="set-consumer-offset-card" innholdClassName="card__content">
			<BodyLong className="blokk-s">
				Setter offset til en consumer for en topic+partisjon. Det er viktig å vite at selv om offsetet blir
				endret, så vil ikke consumere plukke opp endringen i offset før de er startet på nytt. Hvis en consumer
				committer et nytt offset før den har blitt startet på nytt og fått hentet inn endringen, så vil den
				overskrive offsetet fra kafka-manager.
			</BodyLong>

			<TopicSelect availableTopics={props.availableTopics} onTopicChanged={setTopicNameField} />

			<TextField
				label="Topic partition (first partition starts at 0)"
				type="number"
				value={topicPartitionField}
				onChange={e => setTopicPartitionField(e.target.value)}
			/>
			<TextField label="Consumer group id" value={groupIdField} onChange={e => setGroupIdField(e.target.value)} />
			<TextField
				label="Offset"
				type="number"
				value={offsetField}
				onChange={e => setOffsetField(e.target.value)}
			/>

			<Button onClick={handleSetConsumerOffset} variant="tertiary">
				Set offset
			</Button>
		</Card>
	);
}

enum FetchFrom {
	BEGINNING = 'BEGINNING',
	END = 'END',
	OFFSET = 'OFFSET'
}

function ReadFromTopicCard(props: { availableTopics: string[] }) {
	const [isLoading, setIsLoading] = useState<boolean>(false);
	const [topicNameField, setTopicNameField] = useState<string | null>(null);
	const [topicPartitionField, setTopicPartitionField] = useState('0');
	const [fetchFromField, setFetchFromField] = useState<FetchFrom>(FetchFrom.END);
	const [fromOffsetField, setFromOffsetField] = useState('0');
	const [maxRecordsField, setMaxRecordsField] = useState('50');
	const [keyValueFilterField, setKeyValueFilterField] = useState('');

	const [clickedRecord, setClickedRecord] = useState<KafkaRecord | null>(null);
	const [recordsFromTopic, setRecordsFromTopic] = useState<KafkaRecord[]>([]);

	const [startTimeMs, setStartTimeMs] = useState<number | null>(null);
	const [timeTakenMs, setTimeTakenMs] = useState<number>(0);
	const timerRef = useRef<number | null>();

	async function handleReadFromTopic() {
		if (topicNameField == null) {
			errorToast('Topic is missing');
			return;
		}

		const topicPartition = parseInt(topicPartitionField, 10);
		const maxRecords = parseInt(maxRecordsField, 10);

		setRecordsFromTopic([]);
		setStartTimeMs(Date.now());
		setIsLoading(true);

		let fetchFromOffset;

		if (fetchFromField === FetchFrom.BEGINNING) {
			fetchFromOffset = 0;
		} else if (fetchFromField === FetchFrom.END) {
			try {
				const lastRecordOffset = (await getLastRecordOffset({ topicName: topicNameField, topicPartition })).data
					.latestRecordOffset;

				fetchFromOffset = lastRecordOffset - maxRecords;
			} catch (e) {
				setStartTimeMs(null);
				setIsLoading(false);
				errorToast('Klarte ikke å hente siste record offset');
				return;
			}
		} else {
			fetchFromOffset = parseInt(fromOffsetField, 10);
		}

		const request: ReadFromTopicRequest = {
			topicName: topicNameField,
			topicPartition,
			fromOffset: fetchFromOffset,
			maxRecords,
			filterText: keyValueFilterField
		};

		readFromTopic(request)
			.then(res => {
				if (res.data.length === 0) {
					warningToast('Could not find any records in the topic+partition for the given offset');
				} else {
					setRecordsFromTopic(res.data);
				}
			})
			.catch(() => errorToast('Klarte ikke å hente siste record offset'))
			.finally(() => {
				setStartTimeMs(null);
				setIsLoading(false);
			});
	}

	useEffect(() => {
		if (startTimeMs != null) {
			timerRef.current = (setInterval(() => setTimeTakenMs(Date.now() - startTimeMs), 100) as unknown) as number;
		}

		if (startTimeMs == null && timerRef.current != null) {
			clearInterval(timerRef.current);
			timerRef.current = null;
			setTimeTakenMs(0);
		}
	}, [startTimeMs]);

	return (
		<Card
			title="Read records from topic"
			className="read-from-topic-card very-large-card center-horizontal"
			innholdClassName="card__content"
		>
			<BodyShort className="blokk-s">
				Leser meldinger fra en topic+partisjon. Trykk på en av meldingene for å se mer detaljert informasjon
			</BodyShort>

			<TopicSelect availableTopics={props.availableTopics} onTopicChanged={setTopicNameField} />

			<TextField
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
				<TextField
					label="From offset"
					type="number"
					value={fromOffsetField}
					onChange={e => setFromOffsetField(e.target.value)}
				/>
			) : null}

			<TextField
				label="Max records (maximum of records that will be returned, max=100)"
				type="number"
				value={maxRecordsField}
				onChange={e => setMaxRecordsField(e.target.value)}
			/>

			<TextField
				label="Key/value filter (tip: max=1 can be used to reduce waiting)"
				value={keyValueFilterField}
				onChange={e => setKeyValueFilterField(e.target.value)}
			/>

			<Button onClick={handleReadFromTopic} variant="tertiary">
				Fetch
			</Button>

			{isLoading && recordsFromTopic.length === 0 ? (
				<div className="read-from-topic-card__loader">
					<Loader size="2xlarge" />
					<BodyShort className="read-from-topic-card__loader-timer">{toTimerStr(timeTakenMs)}</BodyShort>
				</div>
			) : null}

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
			<Modal open={clickedRecord !== null} onClose={() => setClickedRecord(null)}>
				<Modal.Content>
					<KafkaRecordModalContent record={clickedRecord} />
				</Modal.Content>
			</Modal>
		</Card>
	);
}
