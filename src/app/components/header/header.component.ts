import { RecordingsService } from 'src/app/services/recordings.service';
import { Component, Input, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

export type ActionButton = {
  icon: string | (() => string),
  visible?: () => boolean,
  onClick: () => void,
};

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss'],
})
export class HeaderComponent implements OnInit {

  @Input() title?: string = undefined;
  @Input() actionButtons?: ActionButton[];
  @Input() showCustomContent = false;

  constructor(
    private readonly route: ActivatedRoute,
    protected recordingsService: RecordingsService,
  )
  { }

  ngOnInit(): void {
    //Called after the constructor, initializing input properties, and the first call to ngOnChanges.
    //Add 'implements OnInit' to the class.
    if (this.title === undefined) {
      this.title = this.route.snapshot.routeConfig?.title as string;
    }
  }

  protected actionButtonClick(btn: ActionButton) {
    btn.onClick();
  }

  protected getIcon(button: ActionButton): string | undefined {
    return typeof button.icon === 'function' ? button.icon() : button.icon;
  }

}
