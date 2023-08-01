import { Component } from '@angular/core';
import { MatRippleModule } from '@angular/material/core';
import { MatCardModule } from '@angular/material/card';

@Component({
    selector: 'app-dashboard',
    templateUrl: './dashboard.component.html',
    styleUrls: ['./dashboard.component.scss'],
    imports: [MatRippleModule, MatCardModule],
    standalone: true,
})
export class DashboardComponent { }
