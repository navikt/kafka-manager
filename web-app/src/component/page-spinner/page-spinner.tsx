import React from 'react';
import './page-spinner.css';
import { Loader } from '@navikt/ds-react';

export function PageSpinner() {
	return (
		<div className="page-spinner">
			<Loader size="2xlarge" />
		</div>
	);
}
