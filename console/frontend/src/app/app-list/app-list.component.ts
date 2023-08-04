import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatRippleModule } from '@angular/material/core';
import { MatCardModule } from '@angular/material/card';

import { App } from '../app/app';
import { AppService } from '../app/app.service';

@Component({
    selector: 'app-app-list',
    standalone: true,
    imports: [CommonModule, MatRippleModule, MatCardModule],
    templateUrl: './app-list.component.html',
    styleUrls: ['./app-list.component.scss']
})
export class AppListComponent implements OnInit {
    apps: App[] = [];

    constructor(private appService: AppService) {}

    ngOnInit(): void {
        this.appService.getApps().subscribe(apps => this.apps = apps);
    }
}
