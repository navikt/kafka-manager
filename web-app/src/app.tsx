import React from 'react';
import { ToastContainer } from 'react-toastify';
import './app.css';
import 'react-toastify/dist/ReactToastify.css';
import { KafkaAdmin } from './view/kafka-admin/kafka-admin';
import { Header } from './component/header/header';

function App() {
	return (
		<div className="app kafka-manager">
			<Header />
			<main>
				<KafkaAdmin />
			</main>
			<ToastContainer />
		</div>
	);
}

export default App;
