import { BodyShort } from '@navikt/ds-react';

export const SelectedTopic = (props: { selectedTopic: string | null }) => {
	return props.selectedTopic ? (
		<BodyShort size="small" className="selected-topic-indicator">
			Topic: <strong>{props.selectedTopic}</strong>
		</BodyShort>
	) : (
		<BodyShort size="small" className="no-topic-selected-indicator">
			Topic: <strong>No topic selected</strong>
		</BodyShort>
	);
};
