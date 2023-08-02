import React from 'react';
import cls from 'classnames';
import './card.css';
import { Heading } from '@navikt/ds-react';

interface CardProps {
	title?: string;
	children: any;
	className?: string;
	innholdClassName?: string;
}

export function Card(props: CardProps) {
	return (
		<div className={cls('card', props.className)}>
			{props.title && (
				<Heading size="large" className="card__title">
					{props.title}
				</Heading>
			)}
			<div className={props.innholdClassName}>{props.children}</div>
		</div>
	);
}
