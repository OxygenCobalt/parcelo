// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { NgIf } from '@angular/common';
import { Component, EventEmitter, Output } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { NewDraftForm } from '../new-draft-form';

@Component({
    selector: 'app-new-draft-editor',
    standalone: true,
    imports: [MatButtonModule, MatFormFieldModule, MatInputModule, NgIf, ReactiveFormsModule],
    templateUrl: './new-draft-editor.component.html',
    styleUrls: ['./new-draft-editor.component.scss'],
})
export class NewDraftEditorComponent {
    @Output() formSubmit = new EventEmitter<NewDraftForm>();

    form = this.fb.group({
        apkSet: ['', Validators.required],
        icon: ['', Validators.required],
        label: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(30)]],
    });

    constructor(private fb: NonNullableFormBuilder) {}

    emitForm(): void {
        const apkSet = (<HTMLInputElement>document.getElementById("apkset")).files?.[0];
        const icon = (<HTMLInputElement>document.getElementById("icon")).files?.[0];

        if (apkSet !== undefined && icon !== undefined && this.form.value.label !== undefined) {
            const form: NewDraftForm = {
                apkSet: apkSet,
                icon: icon,
                label: this.form.value.label,
            };

            this.formSubmit.emit(form);
        }
    }
}
