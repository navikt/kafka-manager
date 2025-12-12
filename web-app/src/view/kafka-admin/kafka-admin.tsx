import React, { useEffect, useRef, useState } from 'react';
import { errorToast, successToast, warningToast } from '../../utils/toast-utils';
import { Card } from '../../component/card/card';
import { BodyShort, Button, Label, Loader, Modal, TextField, ToggleGroup, UNSAFE_Combobox } from '@navikt/ds-react';
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
import './kafka-admin.css';
import { KafkaRecordModalContent } from './kafka-record-modal-content';
import { PageSpinner } from '../../component/page-spinner/page-spinner';
import { toTimerStr } from '../../utils/date-utils';
import { DeleteRecordsCard } from './DeleteRecordsCard';
import { SelectedTopic } from './SelectedTopic';

export function KafkaAdmin() {
	const [availableTopics, setAvailableTopics] = useState<string[] | null>(null);
	const [selectedTopic, setSelectedTopic] = useState<string | null>(null);

	useEffect(() => {
		getAvailableTopics()
			.then(res => {
				setAvailableTopics(res.data);
			})
			.catch(e => {
				errorToast('Unable to load available topics');
				setAvailableTopics([]);
			});
	}, []);

	if (availableTopics == null) {
		return <PageSpinner />;
	}

	const sortedTopics = availableTopics.sort();

	return (
		<div className="view kafka-admin">
			<div className="kafka-admin__topic-selector">
				<UNSAFE_Combobox
					label="Select Kafka Topic"
					options={sortedTopics.map(topic => topic)}
					onToggleSelected={(option) => setSelectedTopic(option)}
					selectedOptions={selectedTopic ? [selectedTopic] : []}
					isMultiSelect={false}
					shouldAutocomplete={true}
				/>
			</div>
			<div className="kafka-admin__content">
				<div className="kafka-admin__col">
					<ConsumerOffsetsCard selectedTopic={selectedTopic} />
					<LastRecordOffsetCard selectedTopic={selectedTopic} />
					<SetConsumerOffsetCard selectedTopic={selectedTopic} />
					<DeleteRecordsCard selectedTopic={selectedTopic} />
				</div>
				<div>
					<ReadFromTopicCard selectedTopic={selectedTopic} />
				</div>
			</div>
		</div>
	);
}

function ConsumerOffsetsCard(props: { selectedTopic: string | null }) {
	const [groupIdField, setGroupIdField] = useState('');
	const [topicPartitionOffsets, setTopicPartitionOffsets] = useState<TopicPartitionOffset[]>([]);

	function handleHentConsumerOffsets() {
		if (props.selectedTopic == null) {
			errorToast('Please select a topic from the sidebar');
			return;
		}

		const request: GetConsumerOffsetsRequest = { groupId: groupIdField, topicName: props.selectedTopic };

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
			<BodyShort spacing>
				Henter siste commitet offset for alle partisjoner tilhørende en consumer gruppe for en gitt topic
			</BodyShort>
			<SelectedTopic selectedTopic={props.selectedTopic} />
			<TextField label="Consumer group id" value={groupIdField} onChange={e => setGroupIdField(e.target.value)} />
			<Button onClick={handleHentConsumerOffsets} variant="tertiary" disabled={!props.selectedTopic}>
				Fetch
			</Button>
			{topicPartitionOffsets.length > 0 && (
				<ul>
					{topicPartitionOffsets.map((tpo, idx) => {
						return (
							<li key={idx}>
								Partition={tpo.topicPartition} Offset={tpo.offset}
							</li>
						);
					})}
				</ul>
			)}
		</Card>
	);
}

function LastRecordOffsetCard(props: { selectedTopic: string | null }) {
	const [topicPartition, setTopicPartition] = useState('0');
	const [lastRecordOffset, setLastRecordOffset] = useState<number | null>(null);

	function handleHentLastRecordOffset() {
		if (props.selectedTopic == null) {
			errorToast('Please select a topic from the sidebar');
			return;
		}

		const request: GetLastRecordOffsetRequest = {
			topicName: props.selectedTopic,
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
			<BodyShort spacing>
				Henter offset til siste record(melding på kafka) som ligger på en topic+partisjon
			</BodyShort>
			<SelectedTopic selectedTopic={props.selectedTopic} />
			<TextField
				label="Topic partition (first partition starts at 0)"
				type="number"
				value={topicPartition}
				onChange={e => setTopicPartition(e.target.value)}
			/>
			<Button onClick={handleHentLastRecordOffset} variant="tertiary" disabled={!props.selectedTopic}>
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

function SetConsumerOffsetCard(props: { selectedTopic: string | null }) {
	const [groupIdField, setGroupIdField] = useState('');
	const [topicPartitionField, setTopicPartitionField] = useState('0');
	const [offsetField, setOffsetField] = useState('0');

	function handleSetConsumerOffset() {
		if (props.selectedTopic == null) {
			errorToast('Please select a topic from the sidebar');
			return;
		}

		const request: SetConsumerOffsetRequest = {
			topicName: props.selectedTopic,
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
			<BodyShort spacing>
				Setter offset til en consumer for en topic+partisjon. Det er viktig å vite at selv om offsetet blir
				endret, så vil ikke consumere plukke opp endringen i offset før de er startet på nytt. Hvis en consumer
				committer et nytt offset før den har blitt startet på nytt og fått hentet inn endringen, så vil den
				overskrive offsetet fra kafka-manager.
			</BodyShort>
			<SelectedTopic selectedTopic={props.selectedTopic} />
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

			<Button onClick={handleSetConsumerOffset} variant="tertiary" disabled={!props.selectedTopic}>
				Set offset
			</Button>
		</Card>
	);
}

function ReadFromTopicCard(props: { selectedTopic: string | null }) {
	const [isLoading, setIsLoading] = useState<boolean>(false);
	const [partitionMode, setPartitionMode] = useState<'all' | 'specific'>('all');
	const [topicPartitionField, setTopicPartitionField] = useState('0');
	const [fetchFromField, setFetchFromField] = useState<'BEGINNING' | 'END' | 'OFFSET'>('END');
	const [fromOffsetField, setFromOffsetField] = useState('0');
	const [maxRecordsField, setMaxRecordsField] = useState('50');
	const [keyValueFilterField, setKeyValueFilterField] = useState('');

	const [clickedRecord, setClickedRecord] = useState<KafkaRecord | null>(null);
	const [recordsFromTopic, setRecordsFromTopic] = useState<KafkaRecord[]>([]);

	const [startTimeMs, setStartTimeMs] = useState<number | null>(null);
	const [timeTakenMs, setTimeTakenMs] = useState<number>(0);
	const timerRef = useRef<number | null>(null);

	async function handleReadFromTopic() {
		if (props.selectedTopic == null) {
			errorToast('Please select a topic from the sidebar');
			return;
		}

		const topicPartition = parseInt(topicPartitionField, 10);
		const maxRecords = parseInt(maxRecordsField, 10);

		setRecordsFromTopic([]);
		setStartTimeMs(Date.now());
		setIsLoading(true);

		let fetchFromOffset;

		if (fetchFromField === 'BEGINNING') {
			fetchFromOffset = 0;
		} else if (fetchFromField === 'END') {
			try {
				const lastRecordOffset = (await getLastRecordOffset({ topicName: props.selectedTopic, topicPartition })).data
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
			topicName: props.selectedTopic,
			topicAllPartitions: partitionMode === 'all',
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
			<BodyShort spacing>
				Leser meldinger fra en topic+partisjon. Trykk på en av meldingene for å se mer detaljert informasjon
			</BodyShort>

			<SelectedTopic selectedTopic={props.selectedTopic} />

			<div className="toggle-group-container">
				<div className="toggle-group-wrapper">
					<Label className="toggle-group-label">Topic partitions</Label>
					<ToggleGroup value={partitionMode} onChange={(value) => setPartitionMode(value as 'all' | 'specific')} size="small">
						<ToggleGroup.Item value="all">All</ToggleGroup.Item>
						<ToggleGroup.Item value="specific">Specific</ToggleGroup.Item>
					</ToggleGroup>
				</div>

				<div className="toggle-group-wrapper">
					<Label  className="toggle-group-label">Fetch from</Label>
					<ToggleGroup value={fetchFromField} onChange={(value) => setFetchFromField(value as 'BEGINNING' | 'END' | 'OFFSET')} size="small">
						<ToggleGroup.Item value="BEGINNING">Beginning</ToggleGroup.Item>
						<ToggleGroup.Item value="END">End</ToggleGroup.Item>
						<ToggleGroup.Item value="OFFSET">Offset</ToggleGroup.Item>
					</ToggleGroup>
				</div>
			</div>

			{partitionMode === 'specific' && (
				<TextField
					label="Topic partition number (first partition starts at 0)"
					type="number"
					value={topicPartitionField}
					onChange={e => setTopicPartitionField(e.target.value)}
				/>
			)}

			{fetchFromField === 'OFFSET' && (
				<TextField
					label="From offset"
					type="number"
					value={fromOffsetField}
					onChange={e => setFromOffsetField(e.target.value)}
				/>
			)}

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

			<Button onClick={handleReadFromTopic} variant="tertiary" disabled={!props.selectedTopic}>
				Fetch
			</Button>

			{isLoading && recordsFromTopic.length === 0 ? (
				<div className="read-from-topic-card__loader">
					<Loader size="2xlarge" />
					<BodyShort className="read-from-topic-card__loader-timer">{toTimerStr(timeTakenMs)}</BodyShort>
				</div>
			) : null}

			{recordsFromTopic.length > 0 ? (
				<table className="tabell">
					<thead>
						<tr>
							<th>Partition</th>
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
										key={`${record.partition}-${record.offset}`}
										onClick={() => setClickedRecord(record)}
										className="kafka-record-row"
									>
										<td>{record.partition}</td>
										<td>{record.offset}</td>
										<td>{record.key || 'NO_KEY'}</td>
										<td className="kafka-record-value">{record.value}</td>
									</tr>
								);
							})}
					</tbody>
				</table>
			) : null}
			<Modal open={clickedRecord !== null} onClose={() => setClickedRecord(null)} aria-label="kafka record content">
				<Modal.Body>
					<KafkaRecordModalContent record={clickedRecord} />
				</Modal.Body>
			</Modal>
		</Card>
	);
}
