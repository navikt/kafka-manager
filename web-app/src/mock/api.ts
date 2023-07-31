import { rest } from 'msw';
import type { RequestHandler } from 'msw';
import { KafkaRecord, LastRecordOffsetResponse, TopicPartitionOffset } from '../api';

const availableTopics = ['test-topic-b', 'test-topic-a', 'test-topic-c'];

const kafkaRecords: KafkaRecord[] = [];

for (let i = 0; i < 25; i++) {
	const key = (i + 123) * (i + 99999);
	const offset = i + 10000;

	kafkaRecords.push({
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

export const handlers: RequestHandler[] = [
	rest.get('/api/kafka/available-topics', (req, res, ctx) => {
		return res(ctx.delay(500), ctx.json(availableTopics));
	}),
	rest.post('/api/kafka/read-topic', (req, res, ctx) => {
		return res(ctx.delay(1000), ctx.json(kafkaRecords));
	}),
	rest.post('/api/kafka/get-consumer-offsets', (req, res, ctx) => {
		return res(ctx.delay(500), ctx.json(topicPartitionOffsets));
	}),
	rest.post('/api/kafka/get-last-record-offset', (req, res, ctx) => {
		return res(ctx.delay(500), ctx.json(lastRecordOffsetResponse));
	}),
	rest.post('/api/kafka/set-consumer-offset', (req, res, ctx) => {
		return res(ctx.delay(500), ctx.status(200));
	})
];
