import { delay, http, HttpResponse, RequestHandler } from 'msw';
import { KafkaRecord, LastRecordOffsetResponse, TopicPartitionOffset } from '../api';

const availableTopics = ['test-topic-b', 'test-topic-a', 'test-topic-c','test-topic-d', 'test-topic-e', 'test-topic-f','test-topic-h'];

const kafkaRecords: KafkaRecord[] = [];

for (let i = 0; i < 25; i++) {
	const key = mockUuidv4();
	const offset = i + 10000;

	kafkaRecords.push({
		partition: 0,
		key: key.toString(),
		value:
			'{"aktoerid":"xxxxxxx","fodselsnr":"xxxxxxxx","formidlingsgruppekode":"ARBS","iserv_fra_dato":null,"etternavn":"TESTERSEN","fornavn":"TEST","nav_kontor":"0425","kvalifiseringsgruppekode":"IKVAL","rettighetsgruppekode":"IYT","hovedmaalkode":"SKAFFEA","sikkerhetstiltak_type_kode":null,"fr_kode":null,"har_oppfolgingssak":true,"sperret_ansatt":false,"er_doed":false,"doed_fra_dato":null,"endret_dato":"2021-03-28T20:11:12+02:00"}',
		offset,
		timestamp: 1620126765357,
		headers: [
			{
				name: 'CORRELATION_ID',
				value: 'ddemc238fsdf0fd3s22'
			}
		]
	});
}

const lastRecordOffsetResponse: LastRecordOffsetResponse = {
	latestRecordOffset: 1234
};

const topicPartitionOffsets: TopicPartitionOffset[] = [
	{
		topicName: 'test-topic',
		topicPartition: 0,
		offset: 4567
	},
	{
		topicName: 'test-topic',
		topicPartition: 1,
		offset: 4570
	}
];

function mockUuidv4(): string {
	return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
		const r = Math.floor(Math.random() * 16);
		const v = c === 'x' ? r : Math.floor(r % 4) + 8;
		return v.toString(16);
	});
}

export const handlers: RequestHandler[] = [
	http.get('/api/kafka/available-topics', async () => {
		return HttpResponse.json(availableTopics);
	}),
	http.post('/api/kafka/read-topic', async () => {
		await delay(500);
		return HttpResponse.json(kafkaRecords);
	}),
	http.post('/api/kafka/get-consumer-offsets', async () => {
		await delay(500);
		return HttpResponse.json(topicPartitionOffsets);
	}),
	http.post('/api/kafka/get-last-record-offset', async () => {
		await delay(500);
		return HttpResponse.json(lastRecordOffsetResponse);
	}),
	http.post('/api/kafka/set-consumer-offset', async () => {
		await delay(500);
		return HttpResponse.json(null, {status: 200});
	})
];
