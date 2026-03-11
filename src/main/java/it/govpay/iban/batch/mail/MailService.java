package it.govpay.iban.batch.mail;

import org.springframework.stereotype.Service;

import it.govpay.common.configurazione.service.ConfigurazioneService;
import it.govpay.common.mail.AbstractMailService;

@Service
public class MailService extends AbstractMailService {

    public MailService(ConfigurazioneService configurazioneService) {
        super(configurazioneService);
    }

}
