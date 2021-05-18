import NavFrontendSpinner from 'nav-frontend-spinner';
import React from 'react';
import './page-spinner.less';

export function PageSpinner() {
	return (
		<div className="page-spinner">
			<NavFrontendSpinner type="XL" />
		</div>
	);
}