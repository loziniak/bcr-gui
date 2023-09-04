import { TagsDatabaseComponent } from 'src/app/components/tags-database/tags-database.component';
import { SortMode } from 'src/app/pipes/recordings-sort.pipe';
import { MessageBoxService } from 'src/app/services/message-box.service';
import { RecordingsService } from 'src/app/services/recordings.service';
import { Component } from '@angular/core';
import { ModalController } from '@ionic/angular';
import { AppDateTimeFormat, SettingsService } from '../../services/settings.service';

@Component({
  selector: 'app-settings',
  templateUrl: './settings.page.html',
  styleUrls: ['./settings.page.scss'],
})
export class SettingsPage {

  SortMode = SortMode;

  // sample datetime (last second of current year)
  readonly dateTimeSample = new Date(new Date().getFullYear(), 11, 31, 23, 59, 59);

  constructor(
    private mbs: MessageBoxService,
    private modalCtrl: ModalController,
    protected settings: SettingsService,
    protected recordingsService: RecordingsService,
  ) { }

  async ionViewWillLeave() {
    await this.save();
  }

  async save() {
    await this.settings.save();
  }

  selectRecordingsDirectory() {
    this.recordingsService.selectRecordingsDirectory();
  }

  /**
   * Need to re-create the whole object to let pipes update
   */
  updateDateTimeStyle(style: string, key: keyof AppDateTimeFormat) {
    this.settings.dateTimeStyle = { ...this.settings.dateTimeStyle, [key]: style === '' ? undefined : style }
  }

  async openTagsEditor() {
    // show editor
    const modal = await this.modalCtrl.create({
      component: TagsDatabaseComponent,
      // componentProps: {
      //   recording: item,
      // },
      backdropDismiss: false,
    });
    modal.present();

  }

}
