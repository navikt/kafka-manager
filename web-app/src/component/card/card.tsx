import React from 'react';
import cls from 'classnames';
import { Innholdstittel } from 'nav-frontend-typografi';
import './card.less';

interface CardProps {
	title?: string;
	children: any;
	className?: string;
	innholdClassName?: string;
}

export function Card(props: CardProps) {
	return (
		<div className={cls('card', props.className)}>
			{props.title && <Innholdstittel className="card__title">{props.title}</Innholdstittel>}
			<div className={props.innholdClassName}>{props.children}</div>
		</div>
	);
}
