INSERT INTO MESSAGE_QUEUE(RECIPIENT_INBOX,FUNCTION,PAYLOAD) VALUES
	 ('fsm:converter_monitor','INITIAL',''),
	 ('fsm:loader_monitor','INITIAL',''),
	 ('fsm:crawler_monitor','INITIAL',''),
	 ('fsm:message_queue_monitor','INITIAL',''),
	 ('fsm:process_liveness_monitor','INITIAL',''),
	 ('fsm:file_storage_monitor','INITIAL','');
